package com.zjgsu.oes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Service
public class VideoService {
    private static final long LEASE_MS = 360_000;
    static final long MAX_BYTES = 1024L * 1024 * 1024;
    static final int MAX_TRANSCRIPT_BYTES = 512 * 1024;
    private final VideoRepository videos;
    private final TaskRepository tasks;
    private final NoteRepository notes;
    private final TaskService taskService;
    private final NoteService noteService;
    private final DependencyClient courses;
    private final ModelNotes model;
    private final ObjectMapper json;
    private final TransactionTemplate transaction;
    private final Path storage;
    private final String workerToken;
    private volatile long lastWorkerPoll;
    private volatile boolean workerSupportsLinks;

    public VideoService(VideoRepository videos, TaskRepository tasks, NoteRepository notes,
                        TaskService taskService, NoteService noteService, DependencyClient courses,
                        ModelNotes model, ObjectMapper json, TransactionTemplate transaction,
                        @Value("${VIDEO_STORAGE_DIR:./video-storage}") String storage,
                        @Value("${VIDEO_WORKER_TOKEN:}") String workerToken) {
        this.videos = videos;
        this.tasks = tasks;
        this.notes = notes;
        this.taskService = taskService;
        this.noteService = noteService;
        this.courses = courses;
        this.model = model;
        this.json = json;
        this.transaction = transaction;
        this.storage = Path.of(storage).toAbsolutePath().normalize();
        this.workerToken = workerToken;
    }

    public record Claim(String taskId, String fileName, String lease, String sourceUrl) {}

    public boolean available() {
        return model.configured() && System.currentTimeMillis() - lastWorkerPoll < LEASE_MS;
    }

    public boolean linkAvailable() {
        return available() && workerSupportsLinks;
    }

    @Transactional
    public Task enqueueLink(String courseId, String source, String title, GenerationOptions options) {
        var shared = PublicVideoLink.parse(source);
        String name = title == null || title.isBlank() ? shared.title() : title;
        return enqueueLink(courseId, shared.url(), name, false, noteService.settings(options));
    }

    Task enqueueCollectionPart(String courseId, BilibiliCatalog.Episode episode, String settings) {
        return enqueueLink(courseId, episode.url(), "第 " + episode.index() + " 集 · " + episode.title(), true, settings);
    }

    private Task enqueueLink(String courseId, String url, String title, boolean collection, String settings) {
        if (!linkAvailable()) {
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "本机链接转写尚未就绪，请确认工作进程已安装 yt-dlp");
        }
        String sourceTitle = BilibiliLink.title(title.isBlank() ? "链接视频" : title, 195);
        String fileName = sourceTitle.replaceAll("[/\\\\]", " ") + ".mp4";
        var queued = prepare(courseId, fileName, title.isBlank() ? null : sourceTitle, collection);
        queued.video().sourceUrl = url;
        queued.video().generationOptions = settings;
        videos.save(queued.video());
        return queued.task();
    }

    @Transactional
    public Task enqueue(String courseId, MultipartFile file, GenerationOptions options) {
        if (file.isEmpty() || file.getSize() > MAX_BYTES) {
            throw error(HttpStatus.BAD_REQUEST, "请选择不超过 1 GB 的视频");
        }
        String name = file.getOriginalFilename();
        if (name == null || name.length() > 200 || name.contains("/") || name.contains("\\")
                || !name.toLowerCase(Locale.ROOT).matches(".+\\.(mp4|mov|webm|mkv)")) {
            throw error(HttpStatus.BAD_REQUEST, "请选择 MP4、MOV、WebM 或 MKV 视频");
        }
        if (!available()) {
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "请启动本机转写程序并检查笔记模型配置");
        }
        var queued = prepare(courseId, name, null, false);
        queued.video().generationOptions = noteService.settings(options);
        Path target = filePath(queued.task().id);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    try {
                        Files.deleteIfExists(target);
                    } catch (IOException ignored) {
                        // A rolled-back file is inaccessible without a committed job.
                    }
                }
            }
        });
        try (var input = file.getInputStream()) {
            Files.createDirectories(storage);
            Files.copy(input, target);
        } catch (IOException e) {
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "视频保存失败，请检查本机存储");
        }
        videos.save(queued.video());
        return queued.task();
    }

    private record QueuedVideo(Task task, VideoJob video) {}

    private QueuedVideo prepare(String courseId, String name, String sourceTitle, boolean collection) {
        if (!courseId.matches("[a-f0-9-]{36}")) {
            throw error(HttpStatus.BAD_REQUEST, "课程编号无效");
        }
        String title = json.readTree(courses.read("/api/courses/" + courseId)).path("title").asText();
        var task = collection ? taskService.reserve(courseId) : taskService.savePending(courseId);
        var video = new VideoJob();
        video.taskId = task.id;
        video.fileName = name;
        video.title = sourceTitle == null ? title : sourceTitle;
        video.queuedAt = System.currentTimeMillis();
        return new QueuedVideo(task, video);
    }

    public Claim claim(String token, boolean supportsLinks) {
        authenticate(token);
        workerSupportsLinks = supportsLinks;
        lastWorkerPoll = System.currentTimeMillis();
        var waiting = videos.pending(supportsLinks, PageRequest.of(0, 1));
        if (waiting.isEmpty()) {
            return null;
        }
        return transaction.execute(status -> {
            String id = waiting.getFirst();
            var task = lockedTask(id);
            var video = video(id);
            if (!"PENDING".equals(task.status) || video.claimedAt != null) {
                return null;
            }
            video.lease = UUID.randomUUID().toString();
            video.claimedAt = System.currentTimeMillis();
            task.status = "RUNNING";
            return new Claim(id, video.fileName, video.lease, video.sourceUrl);
        });
    }

    public FileSystemResource file(String id, String token, String lease) {
        authenticate(token);
        verifyLease(video(id), lease);
        var resource = new FileSystemResource(mediaPath(video(id)));
        if (!resource.isReadable()) {
            throw error(HttpStatus.NOT_FOUND, "视频文件已不可读取");
        }
        return resource;
    }

    FileSystemResource media(String id) {
        var resource = new FileSystemResource(mediaPath(video(id)));
        if (!resource.isReadable()) {
            throw error(HttpStatus.NOT_FOUND, "视频文件已不可读取");
        }
        return resource;
    }

    String mediaName(String id) {
        return video(id).fileName;
    }

    @Transactional
    public void renew(String id, String token, String lease) {
        authenticate(token);
        var task = lockedTask(id);
        var video = video(id);
        verifyLease(video, lease);
        requireRunning(task);
        if (notes.existsById(id)) {
            throw error(HttpStatus.CONFLICT, "该视频已进入笔记生成");
        }
        video.claimedAt = System.currentTimeMillis();
        lastWorkerPoll = video.claimedAt;
    }

    @Transactional
    public void storeLinkedMedia(String id, String token, String lease, long size, InputStream input) {
        authenticate(token);
        if (size <= 0 || size > MAX_BYTES) {
            throw error(HttpStatus.BAD_REQUEST, "链接视频超过 1 GB 或内容为空");
        }
        var task = lockedTask(id);
        var video = video(id);
        verifyLease(video, lease);
        requireRunning(task);
        if (video.sourceUrl == null || notes.existsById(id)) {
            throw error(HttpStatus.CONFLICT, "此任务不能接收链接视频");
        }
        Path temporary = null;
        try {
            Files.createDirectories(storage);
            temporary = Files.createTempFile(storage, id + "-", ".part");
            if (Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING) != size) {
                throw error(HttpStatus.BAD_REQUEST, "链接视频传输不完整");
            }
            Files.move(temporary, filePath(id), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "链接视频保存失败，请检查本机存储后重试");
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // An incomplete upload is never served as a video.
                }
            }
        }
    }

    @Transactional
    public void complete(String id, String token, String lease, String text) {
        authenticate(token);
        lastWorkerPoll = System.currentTimeMillis();
        var task = lockedTask(id);
        var video = video(id);
        verifyLease(video, lease);
        if (notes.existsById(id)) {
            return;
        }
        requireRunning(task);
        if (video.sourceUrl != null && !Files.isRegularFile(filePath(id))) {
            throw error(HttpStatus.CONFLICT, "链接视频文件尚未保存");
        }
        if (text.isBlank() || text.getBytes(StandardCharsets.UTF_8).length > MAX_TRANSCRIPT_BYTES) {
            finishFailed(task, "未识别到语音或转写正文超过 512 KB，请检查视频");
            return;
        }
        if (!model.configured()) {
            finishFailed(task, "笔记模型配置不可用，请恢复配置后重新提交");
            return;
        }
        try {
            noteService.queue(task, new NoteService.Source(video.fileName, video.title, text.strip()), video.generationOptions);
        } catch (ResponseStatusException e) {
            finishFailed(task, "所选模型暂不可用，请恢复配置后重试");
            return;
        }
        task.status = "PENDING";
    }

    @Transactional
    public void fail(String id, String token, String lease, String reason) {
        authenticate(token);
        lastWorkerPoll = System.currentTimeMillis();
        var task = lockedTask(id);
        verifyLease(video(id), lease);
        requireRunning(task);
        if (notes.existsById(id)) {
            throw error(HttpStatus.CONFLICT, "该视频已进入笔记生成");
        }
        finishFailed(task, reason);
    }

    @Scheduled(fixedDelay = 15000)
    public void expireClaims() {
        long deadline = System.currentTimeMillis() - LEASE_MS;
        for (String id : videos.expired(deadline)) {
            transaction.executeWithoutResult(status -> {
                var task = lockedTask(id);
                var video = video(id);
                if ("RUNNING".equals(task.status) && video.claimedAt < deadline && !notes.existsById(id)) {
                    if (video.recoveryCount == null || video.recoveryCount == 0) {
                        video.recoveryCount = 1;
                        video.lease = null;
                        video.claimedAt = null;
                        task.status = "PENDING";
                    } else {
                        finishFailed(task, "视频转写再次中断，请手动重试");
                    }
                }
            });
        }
    }

    @Transactional
    public Task retryEpisode(String id) {
        return notes.existsById(id) ? noteService.retry(id) : retry(id);
    }

    @Transactional
    public Task retry(String id) {
        var previous = lockedTask(id);
        if (!"FAILED".equals(previous.status) || notes.existsById(id)) {
            throw error(HttpStatus.CONFLICT, "只有转写失败的视频可以在这里重试");
        }
        var original = video(id);
        if (original.sourceUrl != null) {
            return enqueueLink(previous.courseId, original.sourceUrl, original.title, false, original.generationOptions);
        }
        if (!available() || !Files.isReadable(mediaPath(original))) {
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "转写程序或原视频不可用，请确认后重试");
        }
        var queued = prepare(previous.courseId, original.fileName, original.title, false);
        queued.video().generationOptions = original.generationOptions;
        queued.video().uploadTaskId = original.uploadTaskId == null ? id : original.uploadTaskId;
        videos.save(queued.video());
        return queued.task();
    }

    private Path mediaPath(VideoJob video) {
        return filePath(video.uploadTaskId == null ? video.taskId : video.uploadTaskId);
    }

    private Path filePath(String id) {
        return storage.resolve(id + ".video");
    }

    private Task lockedTask(String id) {
        return tasks.findLocked(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "任务不存在"));
    }

    private VideoJob video(String id) {
        return videos.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "视频任务不存在"));
    }

    private void authenticate(String token) {
        if (workerToken.isBlank() || token == null || !MessageDigest.isEqual(
                workerToken.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) {
            throw error(HttpStatus.FORBIDDEN, "转写程序身份校验失败");
        }
    }

    private void verifyLease(VideoJob video, String lease) {
        if (lease == null || !lease.equals(video.lease) || video.claimedAt == null
                || System.currentTimeMillis() - video.claimedAt >= LEASE_MS) {
            throw error(HttpStatus.CONFLICT, "视频转写任务已失效");
        }
    }

    private void requireRunning(Task task) {
        if (!"RUNNING".equals(task.status)) {
            throw error(HttpStatus.CONFLICT, "视频任务已结束");
        }
    }

    private void finishFailed(Task task, String reason) {
        task.status = "FAILED";
        task.failureReason = reason;
        task.activeCourseId = null;
    }

    private static ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}

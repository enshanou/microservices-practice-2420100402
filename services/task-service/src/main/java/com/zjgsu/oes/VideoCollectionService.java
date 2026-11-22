package com.zjgsu.oes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
class VideoCollectionService {
    private static final Logger log = LoggerFactory.getLogger(VideoCollectionService.class);
    private final BilibiliCatalog catalog;
    private final VideoCollectionRepository collections;
    private final VideoService video;
    private final TaskService tasks;
    private final TransactionTemplate transaction;
    private final ObjectMapper json;
    private final NoteService notes;

    VideoCollectionService(BilibiliCatalog catalog, VideoCollectionRepository collections,
                           VideoService video, TaskService tasks, TransactionTemplate transaction, ObjectMapper json, NoteService notes) {
        this.catalog = catalog;
        this.collections = collections;
        this.video = video;
        this.tasks = tasks;
        this.transaction = transaction;
        this.json = json;
        this.notes = notes;
    }

    record Entry(BilibiliCatalog.Episode episode, String taskId, String problem) {}
    record View(String id, String courseId, String title, boolean active, boolean stopped, List<Entry> entries) {}

    View start(String courseId, String source, String title, GenerationOptions options) {
        if (!video.linkAvailable()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "本机链接转写尚未就绪");
        }
        String settings = notes.settings(options);
        var preview = catalog.preview(source);
        if (preview.episodes().size() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "这条链接只有一集，请选择单集转写");
        }
        var entries = preview.episodes().stream().map(episode -> new Entry(episode, null,
            episode.seconds() > 3600 ? "单集超过 60 分钟" : null)).toList();
        if (entries.stream().allMatch(entry -> entry.problem() != null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "合集没有符合时长限制的分集");
        }
        return transaction.execute(status -> {
            var batch = new VideoCollection();
            batch.id = UUID.randomUUID().toString();
            batch.courseId = courseId;
            batch.generationOptions = settings;
            batch.activeCourseId = courseId;
            batch.title = title == null || title.isBlank() ? preview.collectionTitle() : BilibiliLink.title(title, 200);
            batch.entriesJson = json.writeValueAsString(entries);
            batch.createdAt = System.currentTimeMillis();
            var saved = collections.saveAndFlush(batch);
            advance(saved);
            return view(saved);
        });
    }

    List<View> list() {
        return collections.findAllByOrderByCreatedAtDesc().stream().map(this::view).toList();
    }

    @Transactional
    public View stop(String id) {
        var batch = locked(id);
        if (batch.activeCourseId != null) {
            batch.activeCourseId = null;
            batch.stopped = true;
        }
        return view(batch);
    }

    @Transactional
    public View retry(String id, int index) {
        var batch = locked(id);
        if (batch.activeCourseId != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请等合集处理结束后再重试失败分集");
        }
        var entries = new ArrayList<>(entries(batch));
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            if (entry.episode().index() != index) continue;
            if (entry.taskId() == null || !"FAILED".equals(tasks.get(entry.taskId()).status)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "只有处理失败的分集可以重试");
            }
            var replacement = video.retryEpisode(entry.taskId());
            entries.set(i, new Entry(entry.episode(), replacement.id, null));
            batch.entriesJson = json.writeValueAsString(entries);
            return view(batch);
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "分集不存在");
    }

    @Scheduled(fixedDelay = 3500)
    void process() {
        for (String id : collections.activeIds()) {
            try {
                transaction.executeWithoutResult(status -> advance(locked(id)));
            } catch (ResponseStatusException error) {
                if (error.getStatusCode() == HttpStatus.NOT_FOUND) {
                    transaction.executeWithoutResult(status -> stop(id));
                } else if (error.getStatusCode() != HttpStatus.SERVICE_UNAVAILABLE) {
                    log.warn("Collection {} could not advance: {}", id, error.getStatusCode());
                }
            } catch (RuntimeException error) {
                log.warn("Collection {} could not advance: {}", id, error.getClass().getSimpleName());
            }
        }
    }

    private void advance(VideoCollection batch) {
        if (batch.activeCourseId == null) {
            return;
        }
        if (batch.currentTaskId != null) {
            var current = tasks.get(batch.currentTaskId);
            if ("PENDING".equals(current.status) || "RUNNING".equals(current.status)) {
                return;
            }
        }
        var entries = new ArrayList<>(entries(batch));
        while (batch.nextIndex < entries.size() && entries.get(batch.nextIndex).problem() != null) {
            batch.nextIndex++;
        }
        if (batch.nextIndex >= entries.size()) {
            batch.activeCourseId = null;
            return;
        }
        if (!video.linkAvailable()) {
            return;
        }
        var next = entries.get(batch.nextIndex);
        var task = video.enqueueCollectionPart(batch.courseId, next.episode(), batch.generationOptions);
        entries.set(batch.nextIndex, new Entry(next.episode(), task.id, null));
        batch.currentTaskId = task.id;
        batch.nextIndex++;
        batch.entriesJson = json.writeValueAsString(entries);
    }

    private List<Entry> entries(VideoCollection batch) {
        return json.readValue(batch.entriesJson, new TypeReference<List<Entry>>() {});
    }

    private View view(VideoCollection batch) {
        return new View(batch.id, batch.courseId, batch.title, batch.activeCourseId != null, batch.stopped, entries(batch));
    }

    private VideoCollection locked(String id) {
        return collections.findLocked(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "合集任务不存在"));
    }
}

package com.zjgsu.oes;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Service
public class NoteService {
    private static final long CLAIM_TIMEOUT_MS = 180_000;
    private final TaskRepository tasks;
    private final NoteRepository jobs;
    private final VideoRepository videos;
    private final TaskService taskService;
    private final DependencyClient courses;
    private final ModelNotes model;
    private final ObjectMapper json;
    private final TransactionTemplate transaction;

    public NoteService(TaskRepository tasks, NoteRepository jobs, VideoRepository videos, TaskService taskService,
                       DependencyClient courses, ModelNotes model, ObjectMapper json,
                       TransactionTemplate transaction) {
        this.tasks = tasks;
        this.jobs = jobs;
        this.videos = videos;
        this.taskService = taskService;
        this.courses = courses;
        this.model = model;
        this.json = json;
        this.transaction = transaction;
    }

    record Source(String fileName, String title, String text) {}

    @Transactional
    public Task enqueue(String courseId, GenerationOptions options) {
        if (!model.configured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "请先配置笔记生成模型");
        }
        String response = courses.read("/api/courses/" + courseId + "/transcript");
        var source = json.readValue(response, Source.class);
        var task = taskService.savePending(courseId);
        queue(task, source, settings(options));
        return task;
    }

    @Transactional
    public Task regenerate(String id, GenerationOptions options) {
        var original = ready(id);
        var previous = taskService.get(id);
        return copy(previous, original, options == null ? options(original.generationOptions) : options);
    }

    private Task copy(Task previous, NoteJob original, GenerationOptions options) {
        courses.require("/api/courses/" + previous.courseId);
        var next = taskService.savePending(previous.courseId);
        var copy = prepare(next, new Source(original.fileName, original.title, original.transcript()), settings(options));
        var video = sourceVideo(original);
        copy.sourceVideoTaskId = video == null ? null : video.taskId;
        jobs.save(copy);
        return next;
    }

    NoteJob ready(String id) {
        return jobs.findById(id).filter(job -> job.result() != null).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "该任务暂无已完成的笔记"));
    }

    VideoJob sourceVideo(NoteJob job) {
        return videos.findById(job.sourceVideoTaskId == null ? job.taskId : job.sourceVideoTaskId).orElse(null);
    }

    String version(ModelNotes.Result content) {
        return fingerprint(new ModelNotes.Result(content.summary(), content.sections(), null, content.appearance(), null));
    }

    String mapVersion(MindMap map) {
        return fingerprint(map);
    }

    private String fingerprint(Object content) {
        return java.util.UUID.nameUUIDFromBytes(json.writeValueAsBytes(content)).toString();
    }

    @Transactional
    public NoteJob revise(String id, NoteController.Revision revision) {
        var job = lockNote(id);
        var previous = json.readValue(job.result(), ModelNotes.Result.class);
        checkVersion(version(previous), revision.version());
        var appearance = revision.appearance();
        String summary = appearance == null ? revision.summary().strip() : revision.summary();
        var sections = editedSections(revision.sections(), appearance);
        if (appearance != null) {
            appearance.validate(summary, sections);
        }
        var edited = new ModelNotes.Result(summary, sections, previous.paragraphs(), appearance, MindMap.forResult(previous));
        String signal = NoteStyle.observe(previous, edited);
        if (!signal.isBlank()) {
            String owner = owner(taskService.get(id).courseId);
            if (!owner.isBlank()) {
                job.styleSignal = signal;
                job.styleOwner = owner;
                job.styleSavedAt = System.currentTimeMillis();
            }
        }
        job.longResultJson = json.writeValueAsString(edited);
        return job;
    }

    @Transactional
    public NoteJob reviseMap(String id, NoteController.MapRevision revision) {
        var job = lockNote(id);
        var previous = json.readValue(job.result(), ModelNotes.Result.class);
        checkVersion(mapVersion(MindMap.forResult(previous)), revision.version());
        var appearance = revision.appearance();
        var sections = editedSections(revision.sections(), appearance);
        if (appearance != null) {
            appearance.validate("", sections);
        }
        job.longResultJson = json.writeValueAsString(new ModelNotes.Result(previous.summary(), previous.sections(),
            previous.paragraphs(), previous.appearance(), new MindMap(sections, appearance)));
        return job;
    }

    private NoteJob lockNote(String id) {
        tasks.findLocked(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
        return ready(id);
    }

    private void checkVersion(String expected, String received) {
        if (!expected.equals(received)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "此内容已在其他窗口修改，请重新打开后编辑");
        }
    }

    private java.util.List<ModelNotes.Section> editedSections(java.util.List<NoteController.EditedSection> sections,
                                                              NoteAppearance appearance) {
        return sections.stream().map(section -> new ModelNotes.Section(
            appearance == null ? section.title().strip() : section.title(),
            appearance == null ? section.points().stream().map(String::strip).toList() : section.points(),
            section.startSeconds()
        )).toList();
    }

    void queue(Task task, Source source, String settings) {
        jobs.save(prepare(task, source, settings));
    }

    private NoteJob prepare(Task task, Source source, String settings) {
        var job = new NoteJob();
        job.taskId = task.id;
        job.fileName = source.fileName();
        job.title = source.title();
        if (source.text().getBytes(StandardCharsets.UTF_8).length <= 7000) {
            job.subtitleText = source.text();
        } else {
            job.subtitleText = "";
            job.longTranscript = source.text();
        }
        job.generationOptions = settings;
        job.model = model.selectedModel(options(settings));
        job.queuedAt = System.currentTimeMillis();
        return job;
    }

    GenerationOptions options(String saved) {
        return saved == null ? GenerationOptions.DEFAULT : json.readValue(saved, GenerationOptions.class).normalized();
    }

    String settings(GenerationOptions received) {
        var selected = received == null ? GenerationOptions.DEFAULT : received.normalized();
        model.selectedModel(selected);
        return json.writeValueAsString(selected);
    }

    private String owner(String courseId) {
        return json.readTree(courses.read("/api/courses/" + courseId)).path("ownerId").asText("");
    }

    @Transactional
    public Task retry(String id) {
        var previous = tasks.findLocked(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
        if (!"FAILED".equals(previous.status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只有失败的笔记可以重试");
        }
        var original = jobs.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "笔记任务不存在"));
        return copy(previous, original, options(original.generationOptions));
    }

    private void recoverExpired(String id, long deadline) {
        var task = tasks.findLocked(id).orElseThrow();
        var job = jobs.findById(id).orElseThrow();
        Long progress = job.progressAt == null ? job.startedAt : job.progressAt;
        if (!"RUNNING".equals(task.status) || progress == null || progress >= deadline) return;
        if (job.recoveryCount == null || job.recoveryCount == 0) {
            job.recoveryCount = 1;
            job.startedAt = job.progressAt = null;
            task.status = "PENDING";
        } else {
            finish(task, "笔记生成再次中断，请手动重试");
        }
    }

    private void renew(NoteJob claimed) {
        transaction.executeWithoutResult(status -> {
            var task = tasks.findLocked(claimed.taskId).orElseThrow();
            var job = jobs.findById(claimed.taskId).orElseThrow();
            if (!"RUNNING".equals(task.status) || !Objects.equals(job.startedAt, claimed.startedAt)) {
                throw new IllegalStateException("笔记任务已由其他工作进程接管");
            }
            job.progressAt = System.currentTimeMillis();
        });
    }

    @Scheduled(initialDelayString = "${notes.poll-ms:2500}", fixedDelayString = "${notes.poll-ms:2500}")
    public void processNext() {
        long now = System.currentTimeMillis();
        long deadline = now - CLAIM_TIMEOUT_MS;
        for (String id : jobs.expired(deadline)) {
            transaction.executeWithoutResult(status -> recoverExpired(id, deadline));
        }
        for (String id : jobs.pending(PageRequest.of(0, 1))) {
            NoteJob claimed = transaction.execute(status -> {
                Task task = tasks.findLocked(id).orElseThrow();
                if (!"PENDING".equals(task.status)) {
                    return null;
                }
                NoteJob job = jobs.findById(id).orElseThrow();
                job.startedAt = job.progressAt = System.currentTimeMillis();
                task.status = "RUNNING";
                return job;
            });
            if (claimed != null) {
                generate(claimed);
            }
        }
    }

    private void generate(NoteJob claimed) {
        String result = null;
        String failure = null;
        try {
            var options = options(claimed.generationOptions);
            String style = "";
            if (options.personalize()) {
                try {
                    String owner = owner(taskService.get(claimed.taskId).courseId);
                    if (!owner.isBlank()) style = NoteStyle.instruction(jobs.styleSamples(owner, PageRequest.of(0, 12)));
                } catch (ResponseStatusException ignored) {
                    // Generation can continue with the explicit settings while the course service is unavailable.
                }
            }
            result = model.generate(claimed, options, style, () -> renew(claimed));
        } catch (IllegalStateException e) {
            failure = e.getMessage();
        } catch (RuntimeException e) {
            failure = "笔记生成失败，请检查模型配置或稍后重试";
        }
        String generated = result;
        String reason = failure;
        transaction.executeWithoutResult(status -> {
            Task task = tasks.findLocked(claimed.taskId).orElseThrow();
            NoteJob job = jobs.findById(claimed.taskId).orElseThrow();
            if ("RUNNING".equals(task.status) && Objects.equals(job.startedAt, claimed.startedAt)) {
                job.longResultJson = generated;
                finish(task, reason);
            }
        });
    }

    private void finish(Task task, String failure) {
        task.status = failure == null ? "SUCCEEDED" : "FAILED";
        task.failureReason = failure;
        task.activeCourseId = null;
    }
}

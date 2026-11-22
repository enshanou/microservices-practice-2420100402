package com.zjgsu.oes;
import feign.FeignException;
import feign.codec.DecodeException;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
@Service
public class TaskService {
    private static final Logger log = LoggerFactory.getLogger(TaskService.class);
    private final TaskRepository tasks;
    private final CourseClient courses;
    private final NoteRepository notes;
    private final VideoRepository videos;
    private final VideoCollectionRepository collections;
    public TaskService(TaskRepository tasks, CourseClient courses, NoteRepository notes, VideoRepository videos, VideoCollectionRepository collections) {
        this.tasks = tasks;
        this.courses = courses;
        this.notes = notes;
        this.videos = videos;
        this.collections = collections;
    }
    public Task create(String courseId) {
        verifyCourse(courseId);
        return savePending(courseId);
    }

    private void verifyCourse(String courseId) {
        try {
            var response = courses.findCourse(courseId);
            var course = response.getBody();
            if (course == null || !courseId.equals(course.id())) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid course-service response");
            }
            log.info("Feign course-service courseId={} instance={}", courseId,
                    response.getHeaders().getFirst("X-Course-Instance"));
        } catch (FeignException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Course does not exist", e);
        } catch (DecodeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid course-service response", e);
        } catch (FeignException e) {
            if (e.status() < 0 || e.status() >= 500) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "course-service unavailable or timed out", e);
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid course-service response", e);
        }
    }

    Task savePending(String courseId) {
        if (collections.existsByActiveCourseId(courseId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "这门课正在按合集转写，请在任务记录查看进度");
        }
        return reserve(courseId);
    }

    Task reserve(String courseId) {
        var t=new Task(); t.id=UUID.randomUUID().toString(); t.courseId=courseId;
        t.status="PENDING"; t.activeCourseId=courseId;
        return tasks.saveAndFlush(t);
    }
    public List<Task> list() { return tasks.findAll(); }
    public Task get(String id) { return tasks.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Task does not exist")); }
    @Transactional
    public Task cancelManual(String id) {
        var task = tasks.findLocked(id).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
        if (notes.existsById(id) || videos.existsById(id) ||
                (!"PENDING".equals(task.status) && !"RUNNING".equals(task.status))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只能取消待处理或处理中的手动任务");
        }
        task.status = "FAILED";
        task.failureReason = "已取消手动任务";
        task.activeCourseId = null;
        return task;
    }

    @Transactional
    public Task transition(String id, TaskController.Change body) {
        var t=tasks.findLocked(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Task does not exist"));
        if (notes.existsById(id) || videos.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "自动处理任务由后台更新状态");
        }
        var expected="RUNNING".equals(body.status()) ? "PENDING" : "RUNNING";
        if (!expected.equals(t.status)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Invalid task transition");
        if ("FAILED".equals(body.status()) && (body.failureReason()==null || body.failureReason().isBlank()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"FAILED requires failureReason");
        if (!"FAILED".equals(body.status()) && body.failureReason()!=null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"failureReason only applies to FAILED");
        t.status=body.status(); t.failureReason=body.failureReason();
        t.activeCourseId="RUNNING".equals(t.status) ? t.courseId : null;
        return tasks.saveAndFlush(t);
    }
}

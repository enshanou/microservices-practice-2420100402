package com.zjgsu.courselens;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
@Service
public class TaskService {
    private final TaskRepository tasks;
    private final DependencyClient courses;
    public TaskService(TaskRepository tasks, DependencyClient courses) { this.tasks=tasks; this.courses=courses; }
    public Task create(String courseId) {
        courses.require("/api/courses/"+courseId);
        var t=new Task(); t.id=UUID.randomUUID().toString(); t.courseId=courseId;
        t.status="PENDING"; t.activeCourseId=courseId;
        return tasks.saveAndFlush(t);
    }
    public List<Task> list() { return tasks.findAll(); }
    public Task get(String id) { return tasks.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Task does not exist")); }
    @Transactional
    public Task transition(String id, TaskController.Change body) {
        var t=tasks.findLocked(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Task does not exist"));
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

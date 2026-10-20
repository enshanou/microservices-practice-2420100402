package com.zjgsu.oes.service;

import com.zjgsu.oes.exception.BusinessConflictException;
import com.zjgsu.oes.exception.ResourceNotFoundException;
import com.zjgsu.oes.model.Course;
import com.zjgsu.oes.model.ProcessingTask;
import com.zjgsu.oes.model.TaskStatus;
import com.zjgsu.oes.repository.CourseRepository;
import com.zjgsu.oes.repository.ProcessingTaskRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class CourseProcessingService {
    private final CourseRepository courses;
    private final ProcessingTaskRepository tasks;

    public CourseProcessingService(CourseRepository courses, ProcessingTaskRepository tasks) {
        this.courses = courses;
        this.tasks = tasks;
    }

    public Course createCourse(String ownerId, String title, String sourceReference) {
        return courses.save(new Course(requireText(ownerId, "ownerId", 64),
                requireText(title, "title", 200), requireText(sourceReference, "sourceReference", 500)));
    }

    @Transactional(readOnly = true)
    public List<Course> listCourses() { return courses.findAllByOrderByCreatedAtAscIdAsc(); }

    @Transactional(readOnly = true)
    public Course getCourse(UUID courseId) {
        requireId(courseId, "courseId");
        return courses.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course does not exist: " + courseId));
    }

    public ProcessingTask createTask(UUID courseId) {
        requireId(courseId, "courseId");
        // Serialize creations for the same course across database transactions.
        Course course = courses.findForUpdate(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course does not exist: " + courseId));
        if (tasks.existsByCourseIdAndStatusIn(courseId, List.of(TaskStatus.PENDING, TaskStatus.RUNNING))) {
            throw new BusinessConflictException("Course already has an active processing task");
        }
        return tasks.saveAndFlush(new ProcessingTask(course));
    }

    @Transactional(readOnly = true)
    public ProcessingTask getTask(UUID taskId) {
        requireId(taskId, "taskId");
        return tasks.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Processing task does not exist: " + taskId));
    }

    @Transactional(readOnly = true)
    public List<ProcessingTask> listTasks(UUID courseId) {
        getCourse(courseId);
        return tasks.findByCourseIdOrderByCreatedAtAscIdAsc(courseId);
    }

    public ProcessingTask startTask(UUID taskId) { return transition(taskId, TaskStatus.RUNNING, null); }
    public ProcessingTask completeTask(UUID taskId) { return transition(taskId, TaskStatus.SUCCEEDED, null); }
    public ProcessingTask failTask(UUID taskId, String reason) { return transition(taskId, TaskStatus.FAILED, reason); }
    public ProcessingTask updateStatus(UUID taskId, TaskStatus status, String failureReason) {
        return transition(taskId, status, failureReason);
    }

    private ProcessingTask transition(UUID taskId, TaskStatus target, String reason) {
        requireId(taskId, "taskId");
        if (target == null || target == TaskStatus.PENDING) {
            throw new IllegalArgumentException("Target status must be RUNNING, SUCCEEDED or FAILED");
        }
        String failureReason = target == TaskStatus.FAILED ? requireText(reason, "failureReason", 1000) : null;
        if (target != TaskStatus.FAILED && reason != null && !reason.isBlank()) {
            throw new IllegalArgumentException("failureReason is only allowed for FAILED tasks");
        }
        ProcessingTask task = tasks.findForUpdate(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Processing task does not exist: " + taskId));
        TaskStatus expected = target == TaskStatus.RUNNING ? TaskStatus.PENDING : TaskStatus.RUNNING;
        if (task.status() != expected) {
            throw new BusinessConflictException("Cannot change task from " + task.status() + " to " + target);
        }
        // The caller reports a state; this method does not perform transcription or AI work.
        task.recordStatus(target, failureReason);
        return tasks.saveAndFlush(task);
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        String trimmed = value.strip();
        if (trimmed.length() > maxLength) throw new IllegalArgumentException(field + " is too long");
        return trimmed;
    }

    private static void requireId(UUID id, String field) {
        if (id == null) throw new IllegalArgumentException(field + " must not be null");
    }
}

package com.zjgsu.oes.service;

import com.zjgsu.oes.model.Course;
import com.zjgsu.oes.model.ProcessingTask;
import com.zjgsu.oes.model.TaskStatus;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Sql("/test-data.sql")
class CourseProcessingServiceTests {

    @Autowired
    private CourseProcessingService service;
    private Course course;

    @BeforeEach
    void setUp() {
        course = service.createCourse("oes", "Microservices", "imports/week-04.srt");
    }

    @Test
    void registersCourseWithTrimmedMetadata() {
        Course created = service.createCourse(" oes ", " Java Basics ", " imports/java.srt ");

        assertEquals("oes", created.ownerId());
        assertEquals("Java Basics", created.title());
        assertEquals("imports/java.srt", created.sourceReference());
        assertEquals(created.id(), service.getCourse(created.id()).id());
        assertTrue(service.listTasks(created.id()).isEmpty());
    }

    @Test
    void rejectsBlankCourseMetadata() {
        assertThrows(IllegalArgumentException.class,
                () -> service.createCourse(null, "Java", "imports/java.srt"));
        assertThrows(IllegalArgumentException.class,
                () -> service.createCourse("oes", " ", "imports/java.srt"));
        assertThrows(IllegalArgumentException.class,
                () -> service.createCourse("oes", "Java", ""));
    }

    @Test
    void createsPendingTaskLinkedToExistingCourse() {
        ProcessingTask task = service.createTask(course.id());

        assertEquals(course.id(), task.courseId());
        assertEquals(TaskStatus.PENDING, task.status());
        assertNull(task.failureReason());
        assertEquals(1, service.listTasks(course.id()).size());
    }

    @Test
    void rejectsTaskForUnknownCourse() {
        assertThrows(IllegalArgumentException.class, () -> service.createTask(UUID.randomUUID()));
        assertTrue(service.listTasks(course.id()).isEmpty());
    }

    @Test
    void rejectsDuplicatePendingTaskWithoutAddingAnotherRecord() {
        ProcessingTask task = service.createTask(course.id());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.createTask(course.id()));

        assertEquals("Course already has an active processing task", error.getMessage());
        assertEquals(1, service.listTasks(course.id()).size());
        assertEquals(task.id(), service.getTask(task.id()).id());
    }

    @Test
    void rejectsDuplicateRunningTask() {
        ProcessingTask task = service.createTask(course.id());
        service.startTask(task.id());

        assertThrows(IllegalStateException.class, () -> service.createTask(course.id()));
        assertEquals(TaskStatus.RUNNING, service.getTask(task.id()).status());
        assertEquals(1, service.listTasks(course.id()).size());
    }

    @Test
    void allowsIndependentTasksForDifferentCourses() {
        Course another = service.createCourse("oes", "Java", "imports/java.srt");

        service.createTask(course.id());
        service.createTask(another.id());

        assertEquals(1, service.listTasks(course.id()).size());
        assertEquals(1, service.listTasks(another.id()).size());
    }

    @Test
    void completesTaskAndPreservesHistoryWhenCreatingAnother() {
        ProcessingTask first = service.createTask(course.id());
        service.startTask(first.id());
        ProcessingTask completed = service.completeTask(first.id());
        ProcessingTask second = service.createTask(course.id());

        assertEquals(TaskStatus.SUCCEEDED, completed.status());
        assertEquals(first.createdAt(), completed.createdAt());
        assertNull(completed.failureReason());
        assertNotEquals(first.id(), second.id());
        assertEquals(TaskStatus.PENDING, second.status());
        assertEquals(2, service.listTasks(course.id()).size());
    }

    @Test
    void recordsFailureReasonAndAllowsNewAttempt() {
        ProcessingTask first = service.createTask(course.id());
        service.startTask(first.id());
        ProcessingTask failed = service.failTask(first.id(), " source unavailable ");
        ProcessingTask retry = service.createTask(course.id());

        assertEquals(TaskStatus.FAILED, failed.status());
        assertEquals("source unavailable", failed.failureReason());
        assertNotEquals(first.id(), retry.id());
        assertEquals(TaskStatus.PENDING, retry.status());
        assertEquals(2, service.listTasks(course.id()).size());
    }

    @Test
    void rejectsCompletionBeforeTaskStarts() {
        ProcessingTask task = service.createTask(course.id());

        assertThrows(IllegalStateException.class, () -> service.completeTask(task.id()));
        assertEquals(TaskStatus.PENDING, service.getTask(task.id()).status());
    }

    @Test
    void rejectsChangingAnAlreadyFinishedTask() {
        ProcessingTask task = service.createTask(course.id());
        service.startTask(task.id());
        service.completeTask(task.id());

        assertThrows(IllegalStateException.class, () -> service.startTask(task.id()));
        assertThrows(IllegalStateException.class, () -> service.failTask(task.id(), "late failure"));
        assertEquals(TaskStatus.SUCCEEDED, service.getTask(task.id()).status());
    }

    @Test
    void rejectsBlankFailureReasonWithoutChangingTask() {
        ProcessingTask task = service.createTask(course.id());
        service.startTask(task.id());

        assertThrows(IllegalArgumentException.class, () -> service.failTask(task.id(), " "));
        assertEquals(TaskStatus.RUNNING, service.getTask(task.id()).status());
    }

    @Test
    void rejectsUnknownTaskAndMissingIdentifiers() {
        assertThrows(IllegalArgumentException.class, () -> service.startTask(UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> service.createTask(null));
        assertThrows(IllegalArgumentException.class, () -> service.getTask(null));
    }

    @Test
    void concurrentSubmissionsCreateOnlyOneActiveTask() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        Callable<Boolean> submit = () -> {
            ready.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent test timed out");
            }
            try {
                service.createTask(course.id());
                return true;
            } catch (IllegalStateException conflict) {
                return false;
            }
        };
        try {
            var first = executor.submit(submit);
            var second = executor.submit(submit);
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            release.countDown();

            int successes = (first.get(5, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(5, TimeUnit.SECONDS) ? 1 : 0);

            assertEquals(1, successes);
            assertEquals(1, service.listTasks(course.id()).size());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }
}

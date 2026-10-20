package com.zjgsu.oes.repository;

import com.zjgsu.oes.model.Course;
import com.zjgsu.oes.model.ProcessingTask;
import com.zjgsu.oes.model.TaskStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class RepositoryTests {
    @Autowired CourseRepository courses;
    @Autowired ProcessingTaskRepository tasks;
    @Autowired JdbcTemplate jdbc;

    @Test
    void writesAndReadsCourse() {
        Course course = courses.saveAndFlush(new Course("oes", "Repository test", "imports/test.srt"));
        assertNotNull(course.id());
        assertEquals("Repository test", courses.findById(course.id()).orElseThrow().title());
        assertEquals(1, jdbc.queryForObject("select count(*) from courses where id = ?", Integer.class, course.id().toString()));
    }

    @Test
    void writesTaskWithCourseRelationship() {
        Course course = courses.saveAndFlush(new Course("oes", "Task relation", "imports/test.srt"));
        ProcessingTask task = tasks.saveAndFlush(new ProcessingTask(course));
        assertEquals(course.id(), tasks.findById(task.id()).orElseThrow().courseId());
        assertEquals(1, tasks.findByCourseIdOrderByCreatedAtAscIdAsc(course.id()).size());
        assertTrue(tasks.existsByCourseIdAndStatusIn(course.id(), java.util.List.of(TaskStatus.PENDING)));
    }

    @Test
    void databaseRejectsTwoActiveTasksEvenWithoutServiceCheck() {
        Course course = courses.saveAndFlush(new Course("oes", "Unique active task", "imports/test.srt"));
        tasks.saveAndFlush(new ProcessingTask(course));
        assertThrows(DataIntegrityViolationException.class,
                () -> tasks.saveAndFlush(new ProcessingTask(course)));
    }

    @Test
    void completedHistoryDoesNotBlockNewActiveTask() {
        Course course = courses.saveAndFlush(new Course("oes", "History", "imports/test.srt"));
        ProcessingTask first = new ProcessingTask(course);
        first.recordStatus(TaskStatus.SUCCEEDED, null);
        tasks.saveAndFlush(first);
        ProcessingTask second = new ProcessingTask(course);
        second.recordStatus(TaskStatus.FAILED, "demo failure");
        tasks.saveAndFlush(second);
        tasks.saveAndFlush(new ProcessingTask(course));
        assertEquals(3, tasks.findByCourseIdOrderByCreatedAtAscIdAsc(course.id()).size());
    }

    @Test
    void databaseRejectsTaskReferencingMissingCourse() {
        String missing = UUID.randomUUID().toString();
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into processing_tasks (id, course_id, status, active_course_id, created_at, updated_at) "
                        + "values (?, ?, 'PENDING', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                UUID.randomUUID().toString(), missing, missing));
    }
}

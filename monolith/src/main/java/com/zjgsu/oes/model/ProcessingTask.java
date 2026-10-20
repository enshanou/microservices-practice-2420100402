package com.zjgsu.oes.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "processing_tasks",
        uniqueConstraints = @UniqueConstraint(name = "uk_tasks_active_course", columnNames = "active_course_id"),
        indexes = @Index(name = "idx_tasks_course_created", columnList = "course_id,created_at"))
public class ProcessingTask {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 36)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false, foreignKey = @ForeignKey(name = "fk_tasks_course"))
    private Course course;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "course_id", length = 36, insertable = false, updatable = false)
    private UUID courseId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16)
    private TaskStatus status;

    // Active tasks share the course ID; terminal tasks release this unique slot.
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "active_course_id", length = 36)
    private UUID activeCourseId;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;
    @Column(name = "created_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant updatedAt;

    protected ProcessingTask() { }

    public ProcessingTask(Course course) {
        this.course = course;
        this.courseId = course.id();
        this.status = TaskStatus.PENDING;
        this.activeCourseId = course.id();
        this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        this.updatedAt = createdAt;
    }

    public void recordStatus(TaskStatus status, String failureReason) {
        this.status = status;
        this.failureReason = failureReason;
        this.activeCourseId = status.isActive() ? courseId : null;
        this.updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public UUID id() { return id; }
    public UUID courseId() { return courseId; }
    public TaskStatus status() { return status; }
    public String failureReason() { return failureReason; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}

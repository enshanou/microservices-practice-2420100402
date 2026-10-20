package com.zjgsu.oes.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "courses")
public class Course {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 36)
    private UUID id;

    @Column(name = "owner_id", nullable = false, length = 64)
    private String ownerId;
    @Column(nullable = false, length = 200)
    private String title;
    @Column(name = "source_reference", nullable = false, length = 500)
    private String sourceReference;
    @Column(name = "created_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant createdAt;

    protected Course() { }

    public Course(String ownerId, String title, String sourceReference) {
        this.ownerId = ownerId;
        this.title = title;
        this.sourceReference = sourceReference;
        this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public UUID id() { return id; }
    public String ownerId() { return ownerId; }
    public String title() { return title; }
    public String sourceReference() { return sourceReference; }
    public Instant createdAt() { return createdAt; }
}

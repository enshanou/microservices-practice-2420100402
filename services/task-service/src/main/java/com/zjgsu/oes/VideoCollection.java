package com.zjgsu.oes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.SecondaryTable;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "video_collections")
@SecondaryTable(name = "video_collection_workflow")
class VideoCollection {
    @Id
    @Column(length = 36)
    String id;
    @Column(length = 36, nullable = false)
    String courseId;
    @Column(length = 36, unique = true)
    String activeCourseId;
    @Column(length = 200, nullable = false)
    String title;
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(columnDefinition = "MEDIUMTEXT", nullable = false)
    String entriesJson;
    int nextIndex;
    @Column(length = 36)
    String currentTaskId;
    boolean stopped;
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(table = "video_collection_workflow", columnDefinition = "TEXT")
    public String generationOptions;
    long createdAt;
}

package com.zjgsu.oes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.SecondaryTable;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "video_jobs")
@SecondaryTable(name = "video_job_links")
@SecondaryTable(name = "video_job_workflow")
public class VideoJob {
    @Id
    @Column(length = 36)
    public String taskId;
    @Column(nullable = false, length = 200)
    public String fileName;
    @Column(nullable = false, length = 200)
    public String title;
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(table = "video_job_workflow", columnDefinition = "TEXT")
    public String generationOptions;
    public long queuedAt;
    public Long claimedAt;
    @Column(table = "video_job_workflow")
    public Integer recoveryCount;
    @Column(table = "video_job_workflow", length = 36)
    public String uploadTaskId;
    @Column(length = 36)
    public String lease;
    @Column(table = "video_job_links", length = 500)
    public String sourceUrl;
}

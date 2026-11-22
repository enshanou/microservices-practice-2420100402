package com.zjgsu.oes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.SecondaryTable;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "note_jobs")
@SecondaryTable(name = "note_job_long_content")
@SecondaryTable(name = "note_job_sources")
@SecondaryTable(name = "note_job_workflow")
public class NoteJob {
    @Id
    @Column(length = 36)
    public String taskId;
    @Column(nullable = false, length = 200)
    public String fileName;
    @Column(nullable = false, length = 200)
    public String title;
    @Column(nullable = false, length = 7000)
    public String subtitleText;
    @Column(nullable = false, length = 150)
    public String model;
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(table = "note_job_workflow", columnDefinition = "TEXT")
    public String generationOptions;
    public long queuedAt;
    public Long startedAt;
    @Column(table = "note_job_workflow")
    public Long progressAt;
    @Column(table = "note_job_workflow")
    public Integer recoveryCount;
    @Column(table = "note_job_workflow", length = 36)
    public String styleOwner;
    @Column(table = "note_job_workflow")
    public Long styleSavedAt;
    @Column(table = "note_job_workflow", length = 120)
    public String styleSignal;
    @Column(length = 8000)
    public String resultJson;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(table = "note_job_long_content", columnDefinition = "MEDIUMTEXT")
    public String longTranscript;
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(table = "note_job_long_content", columnDefinition = "MEDIUMTEXT")
    public String longResultJson;

    @Column(table = "note_job_sources", length = 36)
    public String sourceVideoTaskId;

    String transcript() {
        return longTranscript == null ? subtitleText : longTranscript;
    }

    String result() {
        return longResultJson == null ? resultJson : longResultJson;
    }
}

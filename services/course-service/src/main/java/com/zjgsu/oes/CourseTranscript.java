package com.zjgsu.oes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "course_transcripts")
public class CourseTranscript {
    @Id
    @Column(length = 36)
    public String courseId;

    @Column(nullable = false, length = 200)
    public String fileName;

    @Column(nullable = false, length = 7000)
    public String subtitleText;
}

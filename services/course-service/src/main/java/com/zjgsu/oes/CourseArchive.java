package com.zjgsu.oes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "course_archives")
class CourseArchive {
    @Id
    @Column(length = 36)
    String id;

    protected CourseArchive() {}

    CourseArchive(String id) {
        this.id = id;
    }
}

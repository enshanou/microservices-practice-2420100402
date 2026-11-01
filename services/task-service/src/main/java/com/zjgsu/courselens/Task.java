package com.zjgsu.courselens;
import jakarta.persistence.*;
@Entity @Table(name="processing_tasks")
public class Task {
    @Id
    @Column(length=36, nullable=false)
    public String id;
    @Column(length=36, nullable=false)
    public String courseId;
    @Column(length=16, nullable=false)
    public String status;
    @Column(length=36, nullable=true, unique=true)
    public String activeCourseId;
    @Column(length=1000, nullable=true)
    public String failureReason;
}

package com.zjgsu.courselens;
import jakarta.persistence.*;
@Entity @Table(name="courses")
public class Course {
    @Id
    @Column(length=36, nullable=false)
    public String id;
    @Column(length=36, nullable=false)
    public String ownerId;
    @Column(length=200, nullable=false)
    public String title;
    @Column(length=500, nullable=false)
    public String sourceReference;
}

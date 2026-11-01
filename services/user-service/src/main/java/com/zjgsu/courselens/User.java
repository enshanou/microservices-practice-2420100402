package com.zjgsu.courselens;
import jakarta.persistence.*;
@Entity @Table(name="users")
public class User {
    @Id
    @Column(length=36, nullable=false)
    public String id;
    @Column(length=100, nullable=false)
    public String displayName;
}

package com.zjgsu.oes;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
@Service
public class CourseService {
    private final CourseRepository courses;
    private final DependencyClient users;
    public CourseService(CourseRepository courses, DependencyClient users) { this.courses=courses; this.users=users; }
    public Course create(CourseController.Create body) {
        users.require("/api/users/"+body.ownerId());
        var c=new Course(); c.id=UUID.randomUUID().toString(); c.ownerId=body.ownerId();
        c.title=body.title().strip(); c.sourceReference=body.sourceReference().strip();
        return courses.saveAndFlush(c);
    }
    public List<Course> list() { return courses.findAll(); }
    public Course get(String id) { return courses.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Course does not exist")); }
}

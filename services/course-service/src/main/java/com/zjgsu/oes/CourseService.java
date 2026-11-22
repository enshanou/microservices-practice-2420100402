package com.zjgsu.oes;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
@Service
public class CourseService {
    private final CourseRepository courses;
    private final DependencyClient users;
    private final CourseArchiveRepository archives;
    public CourseService(CourseRepository courses, DependencyClient users, CourseArchiveRepository archives) {
        this.courses = courses;
        this.users = users;
        this.archives = archives;
    }
    public Course create(CourseController.Create body) {
        users.require("/api/users/"+body.ownerId());
        var c=new Course(); c.id=UUID.randomUUID().toString(); c.ownerId=body.ownerId();
        c.title=body.title().strip(); c.sourceReference=body.sourceReference().strip();
        return courses.saveAndFlush(c);
    }
    public List<Course> list() { return courses.findVisible(); }
    public Course get(String id) { return courses.findById(id).filter(c -> !archives.existsById(c.id)).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Course does not exist")); }

    @Transactional
    public void archive(String id) {
        var course = courses.findLocked(id).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "课程不存在"));
        if (!archives.existsById(course.id)) archives.save(new CourseArchive(course.id));
    }
}

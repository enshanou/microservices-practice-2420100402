package com.zjgsu.oes.controller;

import com.zjgsu.oes.dto.CourseDtos;
import com.zjgsu.oes.dto.ProcessingTaskDtos;
import com.zjgsu.oes.service.CourseProcessingService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/courses")
public class CourseController {
    private final CourseProcessingService service;
    public CourseController(CourseProcessingService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<CourseDtos.Response> create(@Valid @RequestBody CourseDtos.Create request) {
        var response = CourseDtos.Response.from(service.createCourse(request.ownerId(), request.title(), request.sourceReference()));
        return ResponseEntity.created(URI.create("/api/courses/" + response.id())).body(response);
    }

    @GetMapping
    public List<CourseDtos.Response> list() {
        return service.listCourses().stream().map(CourseDtos.Response::from).toList();
    }

    @GetMapping("/{courseId}")
    public CourseDtos.Response get(@PathVariable UUID courseId) {
        return CourseDtos.Response.from(service.getCourse(courseId));
    }

    @PostMapping("/{courseId}/tasks")
    public ResponseEntity<ProcessingTaskDtos.Response> createTask(@PathVariable UUID courseId) {
        var response = ProcessingTaskDtos.Response.from(service.createTask(courseId));
        return ResponseEntity.created(URI.create("/api/tasks/" + response.id())).body(response);
    }

    @GetMapping("/{courseId}/tasks")
    public List<ProcessingTaskDtos.Response> tasks(@PathVariable UUID courseId) {
        return service.listTasks(courseId).stream().map(ProcessingTaskDtos.Response::from).toList();
    }
}

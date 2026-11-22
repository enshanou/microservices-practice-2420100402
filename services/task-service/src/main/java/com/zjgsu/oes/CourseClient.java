package com.zjgsu.oes;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "course-service")
interface CourseClient {
    record CourseDetails(String id) {}

    @GetMapping("/api/courses/{id}")
    ResponseEntity<CourseDetails> findCourse(@PathVariable("id") String id);
}

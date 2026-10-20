package com.zjgsu.oes.controller;

import com.zjgsu.oes.dto.ProcessingTaskDtos;
import com.zjgsu.oes.service.CourseProcessingService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
public class ProcessingTaskController {
    private final CourseProcessingService service;
    public ProcessingTaskController(CourseProcessingService service) { this.service = service; }

    @GetMapping("/{taskId}")
    public ProcessingTaskDtos.Response get(@PathVariable UUID taskId) {
        return ProcessingTaskDtos.Response.from(service.getTask(taskId));
    }

    @PatchMapping("/{taskId}/status")
    public ProcessingTaskDtos.Response update(@PathVariable UUID taskId,
                                               @Valid @RequestBody ProcessingTaskDtos.UpdateStatus request) {
        return ProcessingTaskDtos.Response.from(service.updateStatus(taskId, request.status(), request.failureReason()));
    }
}

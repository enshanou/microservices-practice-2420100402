package com.zjgsu.oes.dto;

import com.zjgsu.oes.model.ProcessingTask;
import com.zjgsu.oes.model.TaskStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class ProcessingTaskDtos {
    private ProcessingTaskDtos() { }

    public record UpdateStatus(@NotNull TaskStatus status, @Size(max = 1000) String failureReason) { }

    public record Response(UUID id, UUID courseId, TaskStatus status, String failureReason,
                           Instant createdAt, Instant updatedAt) {
        public static Response from(ProcessingTask task) {
            return new Response(task.id(), task.courseId(), task.status(), task.failureReason(),
                    task.createdAt(), task.updatedAt());
        }
    }
}

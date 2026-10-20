package com.zjgsu.oes.dto;

import com.zjgsu.oes.model.Course;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class CourseDtos {
    private CourseDtos() { }

    public record Create(
            @NotBlank @Size(max = 64) String ownerId,
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 500) String sourceReference) { }

    public record Response(UUID id, String ownerId, String title, String sourceReference, Instant createdAt) {
        public static Response from(Course course) {
            return new Response(course.id(), course.ownerId(), course.title(), course.sourceReference(), course.createdAt());
        }
    }
}

package com.zjgsu.courselens;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.List;
@RestController @RequestMapping("/api/courses")
public class CourseController {
    private final CourseService service;
    public CourseController(CourseService service) { this.service=service; }
    public record Create(@NotBlank @Pattern(regexp="[a-f0-9-]{36}") String ownerId,
        @NotBlank @Size(max=200) String title, @NotBlank @Size(max=500) String sourceReference) {}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public Course create(@Valid @RequestBody Create body) { return service.create(body); }
    @GetMapping public List<Course> list() { return service.list(); }
    @GetMapping("/{id}") public Course get(@PathVariable String id) { return service.get(id); }
}

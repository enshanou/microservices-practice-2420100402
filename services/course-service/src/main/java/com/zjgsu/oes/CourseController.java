package com.zjgsu.oes;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.List;
@RestController @RequestMapping("/api/courses")
public class CourseController {
    private final CourseService service;
    private final String instanceId;
    public CourseController(CourseService service, @Value("${COURSE_INSTANCE_ID:${HOSTNAME:local}}") String instanceId) {
        this.service = service;
        this.instanceId = instanceId;
    }
    public record Create(@NotBlank @Pattern(regexp="[a-f0-9-]{36}") String ownerId,
        @NotBlank @Size(max=200) String title, @NotBlank @Size(max=500) String sourceReference) {}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public Course create(@Valid @RequestBody Create body) { return service.create(body); }
    @GetMapping public List<Course> list() { return service.list(); }
    @GetMapping("/{id}")
    public ResponseEntity<Course> get(@PathVariable String id) {
        return ResponseEntity.ok().header("X-Course-Instance", instanceId).body(service.get(id));
    }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable String id) { service.archive(id); }
}

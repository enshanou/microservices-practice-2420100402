package com.zjgsu.oes;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.List;
@RestController @RequestMapping("/api/tasks")
public class TaskController {
    private final TaskService service;
    public TaskController(TaskService service) { this.service=service; }
    public record Create(@NotBlank @Pattern(regexp="[a-f0-9-]{36}") String courseId) {}
    public record Change(@NotNull @Pattern(regexp="RUNNING|SUCCEEDED|FAILED") String status, @Size(max=1000) String failureReason) {}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public Task create(@Valid @RequestBody Create body) { return service.create(body.courseId()); }
    @GetMapping public List<Task> list() { return service.list(); }
    @GetMapping("/{id}") public Task get(@PathVariable String id) { return service.get(id); }
    @PatchMapping("/{id}/status") public Task change(@PathVariable String id,@Valid @RequestBody Change body) { return service.transition(id,body); }
}

package com.zjgsu.courselens;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.List;
@RestController @RequestMapping("/api/users")
public class UserController {
    private final UserService service;
    public UserController(UserService service) { this.service=service; }
    public record Create(@NotBlank @Size(max=100) String displayName) {}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public User create(@Valid @RequestBody Create body) { return service.create(body.displayName()); }
    @GetMapping public List<User> list() { return service.list(); }
    @GetMapping("/{id}") public User get(@PathVariable String id) { return service.get(id); }
}

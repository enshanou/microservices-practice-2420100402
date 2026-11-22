package com.zjgsu.oes;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
@ConditionalOnProperty(name = "auth.enabled", havingValue = "true")
public class AuthController {
    private final AuthService auth;

    AuthController(AuthService auth) {
        this.auth = auth;
    }

    public record Login(@NotBlank @Size(max = 64) String username,
                        @NotBlank @Size(max = 128) String password) {}

    @PostMapping("/login")
    ResponseEntity<AuthService.LoginResult> login(@Valid @RequestBody Login request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(auth.login(request.username(), request.password()));
    }

    @GetMapping("/me")
    ResponseEntity<Map<String, String>> me(
            @RequestHeader(value = "X-Auth-User", defaultValue = "") String user,
            @RequestHeader(value = "X-Auth-Role", defaultValue = "") String role) {
        if (user.isBlank() || (!role.equals("USER") && !role.equals("ADMIN"))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Gateway identity required");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("username", user, "role", role));
    }
}

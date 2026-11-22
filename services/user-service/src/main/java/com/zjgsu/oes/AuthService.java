package com.zjgsu.oes;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Service
@ConditionalOnProperty(name = "auth.enabled", havingValue = "true")
class AuthService {
    private static final String USERNAME = "week09-user";
    private static final String ADMIN_USERNAME = "week09-admin";
    private static final int ITERATIONS = 600_000;
    static final int TOKEN_TTL_SECONDS = 900;
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final String JWT_HEADER = ENCODER.encodeToString(
            "{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.US_ASCII));

    private final ObjectMapper json;
    private final SecretKeySpec signingKey;
    private final Map<String, Account> accounts;

    AuthService(ObjectMapper json,
                @Value("${auth.jwt-secret}") String secret,
                @Value("${auth.user-password-hash}") String userHash,
                @Value("${auth.admin-password-hash}") String adminHash) {
        this.json = json;
        signingKey = new SecretKeySpec(decode(secret, 32), "HmacSHA256");
        accounts = Map.of(USERNAME, account("USER", userHash), ADMIN_USERNAME, account("ADMIN", adminHash));
    }

    record LoginResult(String accessToken, String tokenType, int expiresIn, String role) {}

    LoginResult login(String username, String password) {
        Account account = accounts.get(username);
        Account candidate = account == null ? accounts.get(USERNAME) : account;
        if (!candidate.matches(password) || account == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        long now = Instant.now().getEpochSecond();
        String payload = ENCODER.encodeToString(json.writeValueAsBytes(Map.of(
                "iss", "courselens-user-service", "aud", "courselens-gateway",
                "sub", username, "role", account.role(), "iat", now, "exp", now + TOKEN_TTL_SECONDS)));
        String message = JWT_HEADER + "." + payload;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(signingKey);
            String signature = ENCODER.encodeToString(mac.doFinal(message.getBytes(StandardCharsets.US_ASCII)));
            return new LoginResult(message + "." + signature, "Bearer", TOKEN_TTL_SECONDS, account.role());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not sign access token", e);
        }
    }

    private static Account account(String role, String configuredHash) {
        String[] parts = configuredHash.split(":", -1);
        if (parts.length != 2) throw new IllegalArgumentException("Expected salt:passwordHash");
        byte[] salt = decode(parts[0], 16);
        byte[] hash = decode(parts[1], 32);
        if (hash.length != 32) throw new IllegalArgumentException("Password hash must contain 32 bytes");
        return new Account(role, salt, hash);
    }

    private static byte[] decode(String value, int minimumLength) {
        byte[] bytes = Base64.getUrlDecoder().decode(value);
        if (bytes.length < minimumLength || !ENCODER.encodeToString(bytes).equals(value)) {
            throw new IllegalArgumentException("Invalid authentication configuration");
        }
        return bytes;
    }

    private record Account(String role, byte[] salt, byte[] hash) {
        boolean matches(String password) {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, hash.length * 8);
            try {
                byte[] calculated = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                        .generateSecret(spec).getEncoded();
                return MessageDigest.isEqual(hash, calculated);
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException("Could not verify password", e);
            } finally {
                spec.clearPassword();
            }
        }
    }
}

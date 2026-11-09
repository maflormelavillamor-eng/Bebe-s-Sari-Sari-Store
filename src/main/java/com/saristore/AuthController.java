package com.saristore;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
public class AuthController {
    private final String ownerUsername;
    private final String ownerPassword;

    public AuthController(
            @Value("${OWNER_USERNAME:AntoniettaVillamor}") String ownerUsername,
            @Value("${OWNER_PASSWORD}") String ownerPassword) {
        this.ownerUsername = ownerUsername;
        this.ownerPassword = ownerPassword;
    }

    @PostMapping("/api/login")
    public LoginResult login(@RequestBody LoginRequest body, HttpServletRequest request) {
        if (body == null || !constantTimeEquals(ownerUsername, body.username())
                || !constantTimeEquals(ownerPassword, body.password())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Incorrect username or password");
        }
        HttpSession session = request.getSession(true);
        request.changeSessionId();
        session.setAttribute("ownerAuthenticated", true);
        return new LoginResult(true);
    }

    @PostMapping("/api/logout")
    public LoginResult logout(HttpSession session) {
        if (session != null) session.invalidate();
        return new LoginResult(false);
    }

    private boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

    public record LoginRequest(String username, String password) {}
    public record LoginResult(boolean authenticated) {}
}

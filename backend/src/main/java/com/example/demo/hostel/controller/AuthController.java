package com.example.demo.hostel.controller;

import com.example.demo.hostel.model.User;
import com.example.demo.hostel.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
        try {
            User user = authService.processGoogleLogin(jwt);

            // LinkedHashMap because profile fields may be null (Map.of rejects nulls)
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("id", user.getId());
            body.put("email", user.getEmail());
            body.put("name", user.getName());
            body.put("role", user.getRole().name());
            body.put("fullName", user.getFullName());
            body.put("phoneNumber", user.getPhoneNumber());
            body.put("rollNumber", user.getRollNumber());
            body.put("roomNumber", user.getRoomNumber());
            return ResponseEntity.ok(body);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        }
    }
}
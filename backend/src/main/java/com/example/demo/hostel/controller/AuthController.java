package com.example.demo.hostel.controller;

import com.example.demo.hostel.dto.LoginRequest;
import com.example.demo.hostel.model.User;
import com.example.demo.hostel.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtDecoder jwtDecoder;

    public AuthController(AuthService authService, JwtDecoder jwtDecoder) {
        this.authService = authService;
        this.jwtDecoder = jwtDecoder;
    }

    /**
     * Sign-in from the login page. Public, because a refused account must get
     * this endpoint's 401 or 403 and reason rather than the generic rejection of
     * the bearer token filter; the token is verified here with the same decoder.
     */
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest request) {
        if (request.credential() == null || request.credential().isBlank() || request.loginType() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A Google credential and login type are required");
        }

        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(request.credential());
        } catch (JwtException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Your Google sign-in could not be verified. Please try again.");
        }
        return toBody(authService.login(jwt, request.loginType()));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
        try {
            return ResponseEntity.ok(toBody(authService.processGoogleLogin(jwt)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        }
    }

    private static Map<String, Object> toBody(User user) {
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
        return body;
    }
}
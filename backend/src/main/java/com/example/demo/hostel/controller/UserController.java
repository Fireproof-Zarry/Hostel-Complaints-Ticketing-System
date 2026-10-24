package com.example.demo.hostel.controller;

import com.example.demo.hostel.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Saves the caller's FCM device token. Accepts either a JSON body
     * {"token": "..."} or a ?token= query parameter.
     */
    @PutMapping("/{id}/fcm-token")
    public ResponseEntity<Void> updateFcmToken(
            @PathVariable String id,
            @RequestParam(required = false) String token,
            @RequestBody(required = false) Map<String, String> payload,
            @AuthenticationPrincipal Jwt jwt) {
        String fcmToken = token != null ? token : payload == null ? null : payload.get("token");
        userService.updateFcmToken(id, jwt.getClaimAsString("email"), fcmToken);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/fcm-token")
    public ResponseEntity<Void> clearFcmToken(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        userService.clearFcmToken(id, jwt.getClaimAsString("email"));
        return ResponseEntity.noContent().build();
    }
}

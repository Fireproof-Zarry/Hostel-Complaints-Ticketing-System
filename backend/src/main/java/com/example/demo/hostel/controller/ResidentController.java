package com.example.demo.hostel.controller;

import com.example.demo.hostel.dto.ResidentListChange;
import com.example.demo.hostel.dto.ResidentListText;
import com.example.demo.hostel.dto.ResidentSummary;
import com.example.demo.hostel.service.ResidentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** The hostel's resident list. Admin only (SecurityConfig: /api/admin/residents/**). */
@RestController
@RequestMapping("/api/admin/residents")
public class ResidentController {

    private final ResidentService residentService;

    public ResidentController(ResidentService residentService) {
        this.residentService = residentService;
    }

    /** How many are on the list; with search, the roll numbers containing it. */
    @GetMapping
    public ResidentSummary summary(@RequestParam(required = false) String search) {
        return residentService.summary(search);
    }

    /** What replacing the list with this text would do. Saves nothing. */
    @PostMapping("/preview")
    public ResidentListChange preview(@RequestBody ResidentListText body) {
        return residentService.preview(body.text());
    }

    /** Replaces the whole list with the roll numbers in this text. */
    @PutMapping
    public ResidentListChange replace(@RequestBody ResidentListText body, @AuthenticationPrincipal Jwt jwt) {
        return residentService.replace(body.text(), jwt.getClaimAsString("email"));
    }

    /** Empties the list, so every smail student can sign in again. */
    @DeleteMapping
    public Map<String, Long> clear(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("removed", residentService.clear(jwt.getClaimAsString("email")));
    }

    /** Body: {"rollNumber": "CS23B051"} */
    @PostMapping
    public ResponseEntity<Map<String, String>> add(@RequestBody Map<String, String> body, @AuthenticationPrincipal Jwt jwt) {
        String added = residentService.add(body.get("rollNumber"), jwt.getClaimAsString("email"));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("rollNumber", added));
    }

    @DeleteMapping("/{rollNumber}")
    public ResponseEntity<Void> remove(@PathVariable String rollNumber) {
        residentService.remove(rollNumber);
        return ResponseEntity.noContent().build();
    }
}

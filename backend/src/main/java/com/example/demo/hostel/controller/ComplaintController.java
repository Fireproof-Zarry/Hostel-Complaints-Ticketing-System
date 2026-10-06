package com.example.demo.hostel.controller;

import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.service.ComplaintService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/complaints")
public class ComplaintController {

    private final ComplaintService complaintService;

    public ComplaintController(ComplaintService complaintService) {
        this.complaintService = complaintService;
    }

    @PostMapping
    public ResponseEntity<Complaint> createComplaint(@AuthenticationPrincipal Jwt jwt, @RequestBody Map<String, String> payload) {
        String email = jwt.getClaimAsString("email");
        String title = payload.get("title");
        String description = payload.get("description");
        String category = payload.get("category");
        String floor = payload.get("floor");
        String room = payload.get("room");

        if (title == null || title.trim().isEmpty()
            || description == null || description.trim().isEmpty()
            || category == null
            || floor == null
            || room == null) {
            return ResponseEntity.badRequest().build();
        }

        Complaint savedComplaint = complaintService.createComplaint(email, title, description, category, floor, room);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedComplaint);
    }

    @GetMapping("/mine")
    public ResponseEntity<List<Complaint>> getMyComplaints(@AuthenticationPrincipal Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        return ResponseEntity.ok(complaintService.getMyComplaints(email));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Complaint> getComplaintById(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        return ResponseEntity.ok(complaintService.getComplaintById(id, email));
    }
}
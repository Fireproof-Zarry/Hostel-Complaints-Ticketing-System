package com.example.demo.hostel.controller;

import com.example.demo.hostel.dto.CreateComplaintRequest;
import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.service.ComplaintService;
import com.example.demo.hostel.service.UpvoteService;
import com.example.demo.hostel.validation.RoomLocationValidator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/complaints")
public class ComplaintController {

    private final ComplaintService complaintService;
    private final RoomLocationValidator roomLocationValidator;
    private final UpvoteService upvoteService;

    public ComplaintController(
            ComplaintService complaintService,
            RoomLocationValidator roomLocationValidator,
            UpvoteService upvoteService) {
        this.complaintService = complaintService;
        this.roomLocationValidator = roomLocationValidator;
        this.upvoteService = upvoteService;
    }

    private static final int MAX_WING_LENGTH = 100;
    private static final int MAX_TIME_SLOT_LENGTH = 500;

    @PostMapping
    public ResponseEntity<Complaint> createComplaint(@AuthenticationPrincipal Jwt jwt, @RequestBody CreateComplaintRequest payload) {
        String email = jwt.getClaimAsString("email");
        String title = trimToNull(payload.title());
        String description = trimToNull(payload.description());
        String category = trimToNull(payload.category());
        String availableTimeSlot = trimToNull(payload.availableTimeSlot());

        if (title == null || description == null || category == null
                || (availableTimeSlot != null && availableTimeSlot.length() > MAX_TIME_SLOT_LENGTH)) {
            return ResponseEntity.badRequest().build();
        }

        String floor = null;
        String wing = null;
        if (payload.communityArea()) {
            // Community areas have no room; the floor and wing locate the issue
            floor = roomLocationValidator.canonicalFloor(payload.floor());
            wing = trimToNull(payload.wing());
            if (floor == null || wing == null || wing.length() > MAX_WING_LENGTH) {
                return ResponseEntity.badRequest().build();
            }
        }
        // Personal issues: the room comes from the student's profile, so any
        // floor/room in the payload is ignored

        CreateComplaintRequest request = new CreateComplaintRequest(
                title, description, category, payload.communityArea(), floor, null, wing,
                availableTimeSlot, payload.keyLeftWithSecurity());
        Complaint savedComplaint = complaintService.createComplaint(email, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedComplaint);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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

    @PostMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Complaint> uploadImage(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        return ResponseEntity.ok(complaintService.attachImage(id, email, file));
    }

    /**
     * Toggles the signed-in student's upvote on a community-area complaint.
     * Returns {"complaintId", "upvoted", "upvoteCount"}.
     */
    @PostMapping("/{id}/upvote")
    public ResponseEntity<Map<String, Object>> toggleUpvote(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        String userId = upvoteService.userIdForEmail(jwt.getClaimAsString("email"));

        boolean upvoted;
        try {
            upvoted = upvoteService.toggleUpvote(id, userId);
        } catch (DataIntegrityViolationException e) {
            // A simultaneous request (double click) already added this upvote
            upvoted = true;
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("complaintId", id);
        body.put("upvoted", upvoted);
        body.put("upvoteCount", upvoteService.countUpvotes(id));
        return ResponseEntity.ok(body);
    }
}

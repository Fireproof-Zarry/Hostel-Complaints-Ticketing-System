package com.example.demo.hostel.controller;

import com.example.demo.hostel.dto.CommunityComplaintPage;
import com.example.demo.hostel.dto.ComplaintHistoryEntry;
import com.example.demo.hostel.dto.CreateComplaintRequest;
import com.example.demo.hostel.dto.DuplicateCandidate;
import com.example.demo.hostel.dto.MyComplaint;
import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.service.CommunityComplaintService;
import com.example.demo.hostel.service.ComplaintService;
import com.example.demo.hostel.service.DuplicateDetectionService;
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

/**
 * Complaints as their reporters see them: filing, listing, details, history, photos,
 * upvotes and the community dashboard. Hostel management uses AdminController.
 */
@RestController
@RequestMapping("/api/complaints")
public class ComplaintController {

    private final ComplaintService complaintService;
    private final RoomLocationValidator roomLocationValidator;
    private final UpvoteService upvoteService;
    private final DuplicateDetectionService duplicateDetectionService;
    private final CommunityComplaintService communityComplaintService;

    public ComplaintController(
            ComplaintService complaintService,
            RoomLocationValidator roomLocationValidator,
            UpvoteService upvoteService,
            DuplicateDetectionService duplicateDetectionService,
            CommunityComplaintService communityComplaintService) {
        this.complaintService = complaintService;
        this.roomLocationValidator = roomLocationValidator;
        this.upvoteService = upvoteService;
        this.duplicateDetectionService = duplicateDetectionService;
        this.communityComplaintService = communityComplaintService;
    }

    private static final int MAX_WING_LENGTH = 100;
    private static final int MAX_TIME_SLOT_LENGTH = 500;

    /**
     * Files a complaint: 201, or 400 if a required field is missing or too long. Also limited
     * by ComplaintRateLimitFilter and the open-complaint limit in ComplaintService.
     */
    @PostMapping
    public ResponseEntity<Complaint> createComplaint(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody CreateComplaintRequest payload) {

        String email = jwt.getClaimAsString("email");
        String title = trimToNull(payload.title());
        String description = trimToNull(payload.description());
        String category = trimToNull(payload.category());
        String availableTimeSlot = trimToNull(payload.availableTimeSlot());

        if (title == null || description == null || category == null
                || (availableTimeSlot != null
                        && availableTimeSlot.length() > MAX_TIME_SLOT_LENGTH)) {
            return ResponseEntity.badRequest().build();
        }

        String floor = null;
        String wing = null;

        if (payload.communityArea()) {
            floor = roomLocationValidator.canonicalFloor(payload.floor());
            wing = trimToNull(payload.wing());

            if (floor == null || wing == null || wing.length() > MAX_WING_LENGTH) {
                return ResponseEntity.badRequest().build();
            }
        }

        CreateComplaintRequest request = new CreateComplaintRequest(
                title,
                description,
                category,
                payload.communityArea(),
                floor,
                // Used for staff only; students file under their profile room
                payload.communityArea() ? null : trimToNull(payload.room()),
                wing,
                availableTimeSlot,
                payload.keyLeftWithSecurity());

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

    /** The caller's complaints, newest first, with the assignee's contact while one is active. */
    @GetMapping("/mine")
    public ResponseEntity<List<MyComplaint>> getMyComplaints(
            @AuthenticationPrincipal Jwt jwt) {

        String email = jwt.getClaimAsString("email");
        return ResponseEntity.ok(complaintService.getMyComplaintsWithContacts(email));
    }

    /**
     * Community dashboard: open community-area complaints with their upvote
     * count and whether the caller reported / upvoted each one.
     * sort = upvotes (default) | recent; optional floor filter; paged.
     */
    @GetMapping("/community")
    public ResponseEntity<CommunityComplaintPage> getCommunityComplaints(
            @RequestParam(required = false) String floor,
            @RequestParam(defaultValue = "upvotes") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        return ResponseEntity.ok(
                communityComplaintService.getCommunityComplaints(email, floor, sort, page, size));
    }

    /**
     * Open complaints similar to the title a student is typing (up to 3, most
     * similar first). Community-area issues (the default) need a floor and
     * optionally a wing; personal issues (communityArea=false) search the
     * student's own profile room, so no location is sent. Staff and admins have
     * no room of their own and send the room they are reporting on.
     */
    @GetMapping("/potential-duplicates")
    public ResponseEntity<List<DuplicateCandidate>> getPotentialDuplicates(
            @RequestParam String title,
            @RequestParam(defaultValue = "true") boolean communityArea,
            @RequestParam(required = false) String floor,
            @RequestParam(required = false) String wing,
            @RequestParam(required = false) String room,
            @AuthenticationPrincipal Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        return ResponseEntity.ok(
                duplicateDetectionService.findPotentialDuplicates(email, title, communityArea, floor, wing, room));
    }

    /** One of the caller's own complaints; 404 for anyone else's. */
    @GetMapping("/{id}")
    public ResponseEntity<Complaint> getComplaintById(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        String email = jwt.getClaimAsString("email");
        return ResponseEntity.ok(complaintService.getComplaintById(id, email));
    }

    /**
     * A complaint's status changes, newest first. Students get their own complaints (404
     * otherwise); admins and staff get any complaint.
     */
    @GetMapping("/{id}/history")
    public ResponseEntity<List<ComplaintHistoryEntry>> getComplaintHistory(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        String email = jwt.getClaimAsString("email");
        return ResponseEntity.ok(
                complaintService.getComplaintHistory(id, email));
    }

    /** Owner deletes their own PENDING complaint (409 once it is assigned or in progress). */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteComplaint(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        complaintService.deleteComplaint(id, jwt.getClaimAsString("email"));
        return ResponseEntity.noContent().build();
    }

    /** Attaches a photo to the caller's own complaint, replacing any earlier one. */
    @PostMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Complaint> uploadImage(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt) {

        String email = jwt.getClaimAsString("email");
        return ResponseEntity.ok(
                complaintService.attachImage(id, email, file));
    }

    /**
     * Toggles the signed-in student's upvote on a community-area complaint.
     * Returns complaintId, upvoted, and upvoteCount.
     */
    @PostMapping("/{id}/upvote")
    public ResponseEntity<Map<String, Object>> toggleUpvote(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = upvoteService.userIdForEmail(
                jwt.getClaimAsString("email"));

        boolean upvoted;
        try {
            upvoted = upvoteService.toggleUpvote(id, userId);
        } catch (DataIntegrityViolationException e) {
            // A simultaneous request may already have added this upvote.
            upvoted = true;
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("complaintId", id);
        body.put("upvoted", upvoted);
        body.put("upvoteCount", upvoteService.countUpvotes(id));

        return ResponseEntity.ok(body);
    }
}
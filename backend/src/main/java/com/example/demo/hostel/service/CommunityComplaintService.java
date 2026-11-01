package com.example.demo.hostel.service;

import com.example.demo.hostel.dto.CommunityComplaintPage;
import com.example.demo.hostel.dto.CommunityComplaintView;
import com.example.demo.hostel.model.Status;
import com.example.demo.hostel.repository.ComplaintRepository;
import com.example.demo.hostel.repository.UserRepository;
import com.example.demo.hostel.validation.RoomLocationValidator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

/** Community dashboard: open, non-archived community-area complaints that students can upvote. */
@Service
public class CommunityComplaintService {

    static final List<Status> OPEN_STATUSES = List.of(Status.PENDING, Status.ASSIGNED, Status.IN_PROGRESS);
    static final int MAX_PAGE_SIZE = 50;

    private final ComplaintRepository complaintRepository;
    private final UserRepository userRepository;
    private final RoomLocationValidator roomLocationValidator;

    public CommunityComplaintService(
            ComplaintRepository complaintRepository,
            UserRepository userRepository,
            RoomLocationValidator roomLocationValidator) {
        this.complaintRepository = complaintRepository;
        this.userRepository = userRepository;
        this.roomLocationValidator = roomLocationValidator;
    }

    /**
     * @param floor optional floor filter (any accepted spelling, e.g. "2nd floor"); null for all floors
     * @param sort  "upvotes" (most upvoted first) or "recent" (newest first)
     */
    @Transactional(readOnly = true)
    public CommunityComplaintPage getCommunityComplaints(String email, String floor, String sort, int page, int size) {
        String canonicalFloor = null;
        if (floor != null && !floor.isBlank()) {
            canonicalFloor = roomLocationValidator.canonicalFloor(floor);
            if (canonicalFloor == null) {
                throw badRequest("Unknown floor: " + floor);
            }
        }
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw badRequest("page must be >= 0 and size between 1 and " + MAX_PAGE_SIZE);
        }
        String userId = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"))
                .getId();

        PageRequest pageRequest = PageRequest.of(page, size);
        Page<CommunityComplaintView> result = switch (sort == null ? "upvotes" : sort.toLowerCase(Locale.ROOT)) {
            case "upvotes" -> complaintRepository.findCommunityComplaintsByUpvotes(
                    OPEN_STATUSES, canonicalFloor, userId, pageRequest);
            case "recent" -> complaintRepository.findCommunityComplaintsByRecent(
                    OPEN_STATUSES, canonicalFloor, userId, pageRequest);
            default -> throw badRequest("sort must be 'upvotes' or 'recent'");
        };

        return new CommunityComplaintPage(
                result.getContent(), page, size, result.getTotalElements(), result.getTotalPages());
    }

    private static ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }
}

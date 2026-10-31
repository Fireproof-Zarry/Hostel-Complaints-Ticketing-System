package com.example.demo.hostel.service;

import com.example.demo.hostel.dto.DuplicateCandidate;
import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.model.User;
import com.example.demo.hostel.repository.ComplaintRepository;
import com.example.demo.hostel.repository.UpvoteRepository;
import com.example.demo.hostel.repository.UserRepository;
import com.example.demo.hostel.validation.RoomLocationValidator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Finds open complaints similar to one a student is about to file, so they can
 * upvote the existing ticket instead of creating a duplicate. Community-area
 * issues are matched on the same floor; personal issues are matched in the
 * student's own room (useful for multiple-occupancy rooms, and in
 * single-occupancy hostels it catches a student re-filing their own issue).
 */
@Service
public class DuplicateDetectionService {

    /** Shorter titles give meaningless trigram matches. */
    static final int MIN_TITLE_LENGTH = 4;

    /** pg_trgm similarity / word_similarity a match must reach (0 to 1). */
    static final String SIMILARITY_THRESHOLD = "0.3";

    private final ComplaintRepository complaintRepository;
    private final UpvoteRepository upvoteRepository;
    private final UserRepository userRepository;
    private final RoomLocationValidator roomLocationValidator;

    public DuplicateDetectionService(
            ComplaintRepository complaintRepository,
            UpvoteRepository upvoteRepository,
            UserRepository userRepository,
            RoomLocationValidator roomLocationValidator) {
        this.complaintRepository = complaintRepository;
        this.upvoteRepository = upvoteRepository;
        this.userRepository = userRepository;
        this.roomLocationValidator = roomLocationValidator;
    }

    // One transaction so the thresholds set by setTrigramThresholds apply to the search
    @Transactional(readOnly = true)
    public List<DuplicateCandidate> findPotentialDuplicates(
            String email, String title, boolean communityArea, String floor, String wing) {
        String query = title == null ? "" : title.trim();
        if (query.length() < MIN_TITLE_LENGTH) {
            return List.of();
        }
        String canonicalFloor = null;
        if (communityArea) {
            canonicalFloor = roomLocationValidator.canonicalFloor(floor);
            if (canonicalFloor == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid floor is required");
            }
        }
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        List<Long> ids;
        if (communityArea) {
            complaintRepository.setTrigramThresholds(SIMILARITY_THRESHOLD);
            // An empty wing never matches, so it only affects the tie-break ordering
            ids = complaintRepository.findSimilarOpenCommunityComplaintIds(
                    query, canonicalFloor, wing == null ? "" : wing.trim());
        } else {
            // Personal complaints are filed under the profile room, so search that room
            String room = user.getRoomNumber();
            String roomFloor = roomLocationValidator.floorForRoom(room);
            if (roomFloor == null) {
                return List.of();
            }
            complaintRepository.setTrigramThresholds(SIMILARITY_THRESHOLD);
            ids = complaintRepository.findSimilarOpenRoomComplaintIds(query, roomFloor, room);
        }
        if (ids.isEmpty()) {
            return List.of();
        }

        Map<Long, Complaint> complaints = complaintRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Complaint::getId, Function.identity()));
        Map<Long, Long> upvoteCounts = upvoteRepository.countByComplaintIds(ids).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
        Set<Long> upvotedByUser = new HashSet<>(upvoteRepository.findUpvotedComplaintIds(user.getId(), ids));

        // Keep the similarity order returned by the search
        return ids.stream()
                .map(complaints::get)
                .filter(Objects::nonNull)
                .map(complaint -> new DuplicateCandidate(
                        complaint.getId(),
                        complaint.getTitle(),
                        complaint.getStatus(),
                        complaint.getFloor(),
                        complaint.getWing(),
                        complaint.getRoom(),
                        upvoteCounts.getOrDefault(complaint.getId(), 0L),
                        complaint.getCreatedAt(),
                        complaint.getStudent() != null && user.getId().equals(complaint.getStudent().getId()),
                        upvotedByUser.contains(complaint.getId())))
                .toList();
    }
}

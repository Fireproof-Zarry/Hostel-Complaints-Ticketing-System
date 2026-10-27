package com.example.demo.hostel.service;

import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.model.Role;
import com.example.demo.hostel.model.Upvote;
import com.example.demo.hostel.model.UpvoteId;
import com.example.demo.hostel.model.User;
import com.example.demo.hostel.repository.ComplaintRepository;
import com.example.demo.hostel.repository.UpvoteRepository;
import com.example.demo.hostel.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

/**
 * Upvotes ("this affects me too") on community-area complaints. Personal
 * room complaints are private to their student and cannot be upvoted.
 */
@Service
public class UpvoteService {

    private final UpvoteRepository upvoteRepository;
    private final ComplaintRepository complaintRepository;
    private final UserRepository userRepository;

    public UpvoteService(
            UpvoteRepository upvoteRepository,
            ComplaintRepository complaintRepository,
            UserRepository userRepository) {
        this.upvoteRepository = upvoteRepository;
        this.complaintRepository = complaintRepository;
        this.userRepository = userRepository;
    }

    /**
     * Adds the user's upvote if absent, removes it if present.
     *
     * @return true if the upvote was added, false if it was removed
     */
    @Transactional
    public boolean toggleUpvote(Long complaintId, String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (user.getRole() != Role.STUDENT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only students can upvote complaints");
        }

        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Complaint not found"));
        if (complaint.getStudent() != null && userId.equals(complaint.getStudent().getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot upvote your own complaint");
        }
        // Same 404 as a missing complaint, so private complaints are not revealed
        if (!complaint.isCommunityArea() || complaint.isArchived()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Complaint not found");
        }

        Optional<Upvote> existing = upvoteRepository.findById(new UpvoteId(complaintId, userId));
        if (existing.isPresent()) {
            upvoteRepository.delete(existing.get());
            return false;
        }
        // Flush now so a concurrent duplicate fails here (primary key) rather than at commit
        upvoteRepository.saveAndFlush(new Upvote(complaint, user));
        return true;
    }

    public long countUpvotes(Long complaintId) {
        return upvoteRepository.countByIdComplaintId(complaintId);
    }

    public String userIdForEmail(String email) {
        return userRepository.findByEmail(email)
                .map(User::getId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}

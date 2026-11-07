package com.example.demo.hostel.service;

import com.example.demo.hostel.dto.NudgeResponse;
import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.model.Role;
import com.example.demo.hostel.model.Status;
import com.example.demo.hostel.model.User;
import com.example.demo.hostel.repository.ComplaintRepository;
import com.example.demo.hostel.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Lets the owner of an unresolved complaint older than 7 days push the admins,
 * at most once every 7 days. Both waits are strict: exactly 7 days is too early.
 */
@Service
public class NudgeService {

    static final int NUDGE_WAIT_DAYS = 7;

    private static final Set<Status> UNRESOLVED = Set.of(Status.PENDING, Status.ASSIGNED, Status.IN_PROGRESS);

    private final ComplaintRepository complaintRepository;
    private final UserRepository userRepository;
    private final FcmNotificationService fcmNotificationService;

    public NudgeService(
            ComplaintRepository complaintRepository,
            UserRepository userRepository,
            FcmNotificationService fcmNotificationService) {
        this.complaintRepository = complaintRepository;
        this.userRepository = userRepository;
        this.fcmNotificationService = fcmNotificationService;
    }

    public static boolean isUnresolved(Complaint complaint) {
        return UNRESOLVED.contains(complaint.getStatus());
    }

    /** The moment the wait ends, or null if this complaint cannot be nudged at all. */
    public static LocalDateTime nextNudgeAt(Complaint complaint) {
        if (!isUnresolved(complaint) || complaint.getCreatedAt() == null) {
            return null;
        }
        LocalDateTime oldEnough = complaint.getCreatedAt().plusDays(NUDGE_WAIT_DAYS);
        LocalDateTime lastNudged = complaint.getLastNudgedAt();
        if (lastNudged == null) {
            return oldEnough;
        }
        LocalDateTime cooledDown = lastNudged.plusDays(NUDGE_WAIT_DAYS);
        return cooledDown.isAfter(oldEnough) ? cooledDown : oldEnough;
    }

    public static boolean canNudge(Complaint complaint, LocalDateTime now) {
        LocalDateTime next = nextNudgeAt(complaint);
        return next != null && now.isAfter(next);
    }

    /**
     * Records the nudge, then notifies the admins once it is committed. The
     * conditional UPDATE repeats the checks, so of two simultaneous nudges only
     * one succeeds and only one notification goes out.
     */
    @Transactional
    public NudgeResponse nudge(Long complaintId, String email) {
        User requester = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        // Someone else's complaint gets the same 404, so its existence is not revealed
        Complaint complaint = complaintRepository.findByIdAndStudent(complaintId, requester)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Complaint not found"));

        LocalDateTime now = LocalDateTime.now();
        requireNudgeable(complaint, now);

        String title = complaint.getTitle();
        int updated = complaintRepository.recordNudge(complaintId, UNRESOLVED, now, now.minusDays(NUDGE_WAIT_DAYS));
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This complaint was just nudged.");
        }

        List<String> adminTokens = userRepository.findFcmTokensByRole(Role.ADMIN);
        ComplaintTransitionService.runAfterCommit(
                () -> fcmNotificationService.sendNudgeNotification(adminTokens, complaintId, title));

        return new NudgeResponse(complaintId, now, now.plusDays(NUDGE_WAIT_DAYS));
    }

    private static void requireNudgeable(Complaint complaint, LocalDateTime now) {
        if (!isUnresolved(complaint)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only unresolved complaints can be nudged.");
        }
        if (!canNudge(complaint, now)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, complaint.getLastNudgedAt() == null
                    ? "A complaint can be nudged once it is more than " + NUDGE_WAIT_DAYS + " days old."
                    : "This complaint was nudged recently. Try again after " + NUDGE_WAIT_DAYS + " days.");
        }
    }
}

package com.example.demo.hostel.service;

import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.model.ComplaintHistory;
import com.example.demo.hostel.model.Status;
import com.example.demo.hostel.model.User;
import com.example.demo.hostel.repository.ComplaintHistoryRepository;
import com.example.demo.hostel.repository.ComplaintRepository;
import com.example.demo.hostel.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ComplaintTransitionService {

    /**
     * The complaint workflow. ASSIGNED can resolve directly, for small jobs nobody
     * marked as started; PENDING cannot. Going back undoes a mistake or a cancelled
     * assignment. Staff can reopen a closed complaint.
     */
    private static final Map<Status, Set<Status>> ALLOWED_TRANSITIONS = Map.of(
            Status.PENDING, EnumSet.of(Status.ASSIGNED, Status.IN_PROGRESS, Status.REJECTED),
            Status.ASSIGNED, EnumSet.of(Status.IN_PROGRESS, Status.RESOLVED, Status.PENDING, Status.REJECTED),
            Status.IN_PROGRESS, EnumSet.of(Status.RESOLVED, Status.ASSIGNED, Status.PENDING, Status.REJECTED),
            Status.RESOLVED, EnumSet.of(Status.PENDING, Status.IN_PROGRESS),
            Status.REJECTED, EnumSet.of(Status.PENDING, Status.IN_PROGRESS));

    private final ComplaintRepository complaintRepository;
    private final UserRepository userRepository;
    private final ComplaintHistoryRepository complaintHistoryRepository;
    private final FcmNotificationService fcmNotificationService;

    public ComplaintTransitionService(
            ComplaintRepository complaintRepository,
            UserRepository userRepository,
            ComplaintHistoryRepository complaintHistoryRepository,
            FcmNotificationService fcmNotificationService) {
        this.complaintRepository = complaintRepository;
        this.userRepository = userRepository;
        this.complaintHistoryRepository = complaintHistoryRepository;
        this.fcmNotificationService = fcmNotificationService;
    }

    /** Refuses a move the workflow does not allow, with a 409 the frontend can show. */
    public void validateTransition(Status current, Status next) {
        if (!ALLOWED_TRANSITIONS.get(current).contains(next)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A complaint cannot move from " + current + " to " + next);
        }
    }

    /**
     * Updates the complaint's status and records the change in complaint_history
     * in one transaction: if either write fails, neither is committed.
     */
    @Transactional
    public void changeComplaintStatus(Long complaintId, String actorId, String newStatus, String reason) {
        Status nextStatus = parseStatus(newStatus);

        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Complaint not found"));

        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Actor not found"));

        Status oldStatus = complaint.getStatus();
        validateTransition(oldStatus, nextStatus);

        complaint.setStatus(nextStatus);
        complaintRepository.save(complaint);

        ComplaintHistory history = new ComplaintHistory();
        history.setComplaint(complaint);
        history.setActor(actor);
        history.setOldStatus(oldStatus);
        history.setNewStatus(nextStatus);
        history.setReason(reason == null || reason.isBlank() ? null : reason.trim());
        complaintHistoryRepository.save(history);

        User student = complaint.getStudent();
        String fcmToken = student == null ? null : student.getFcmToken();
        if (fcmToken != null && !fcmToken.isBlank() && oldStatus != nextStatus) {
            String title = complaint.getTitle();
            String status = nextStatus.name();
            runAfterCommit(() -> fcmNotificationService.sendStatusUpdateNotification(fcmToken, title, status));
        }
    }

    // Notify only once the change is committed, so a rolled-back update never
    // reaches the student's phone
    private static void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private Status parseStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status is required");
        }
        try {
            return Status.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown status: " + status);
        }
    }
}

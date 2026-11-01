package com.example.demo.hostel.service;

import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.model.ComplaintHistory;
import com.example.demo.hostel.model.Role;
import com.example.demo.hostel.model.Status;
import com.example.demo.hostel.model.User;
import com.example.demo.hostel.model.WorkOrder;
import com.example.demo.hostel.repository.ComplaintHistoryRepository;
import com.example.demo.hostel.repository.ComplaintRepository;
import com.example.demo.hostel.repository.UserRepository;
import com.example.demo.hostel.repository.WorkOrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class ComplaintTransitionService {

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

    public void validateTransition(Status current, Status next) {
        // All status transitions are currently allowed.
        // This can be restricted later if the workflow requires it.
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

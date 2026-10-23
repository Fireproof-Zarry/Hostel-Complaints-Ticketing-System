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
    private final WorkOrderRepository workOrderRepository;
    private final ComplaintHistoryRepository complaintHistoryRepository;
    private final FcmNotificationService fcmNotificationService;

    public ComplaintTransitionService(
            ComplaintRepository complaintRepository,
            UserRepository userRepository,
            WorkOrderRepository workOrderRepository,
            ComplaintHistoryRepository complaintHistoryRepository,
            FcmNotificationService fcmNotificationService) {
        this.complaintRepository = complaintRepository;
        this.userRepository = userRepository;
        this.workOrderRepository = workOrderRepository;
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

    /**
     * Moves the complaint to IN_PROGRESS and creates its work order in one
     * transaction: if either write fails, neither is committed.
     */
    @Transactional
    public WorkOrder assignWorkOrder(Long complaintId, String assigneeId, String priority, LocalDateTime deadline) {
        if (priority == null || priority.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Priority is required");
        }

        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Complaint not found"));

        User assignee = userRepository.findById(assigneeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignee not found"));

        if (assignee.getRole() != Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Complaint can only be assigned to an admin");
        }

        validateTransition(complaint.getStatus(), Status.IN_PROGRESS);

        complaint.setStatus(Status.IN_PROGRESS);
        complaint.setAssignedTo(assignee.getEmail());
        complaintRepository.save(complaint);

        WorkOrder workOrder = new WorkOrder();
        workOrder.setComplaint(complaint);
        workOrder.setAssignee(assignee);
        workOrder.setPriority(priority.trim().toUpperCase(Locale.ROOT));
        workOrder.setDeadline(deadline);
        return workOrderRepository.save(workOrder);
    }
}

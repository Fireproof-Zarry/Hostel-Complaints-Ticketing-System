
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
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class WorkOrderService {

    private final ComplaintRepository complaintRepository;
    private final UserRepository userRepository;
    private final WorkOrderRepository workOrderRepository;
    private final ComplaintHistoryRepository complaintHistoryRepository;
    private final ComplaintTransitionService complaintTransitionService;

    public WorkOrderService(
            ComplaintRepository complaintRepository,
            UserRepository userRepository,
            WorkOrderRepository workOrderRepository,
            ComplaintHistoryRepository complaintHistoryRepository,
            ComplaintTransitionService complaintTransitionService) {

        this.complaintRepository = complaintRepository;
        this.userRepository = userRepository;
        this.workOrderRepository = workOrderRepository;
        this.complaintHistoryRepository = complaintHistoryRepository;
        this.complaintTransitionService = complaintTransitionService;
    }

    /**
     * Assigns a complaint and creates its work order in one transaction.
     */
    @Transactional
    public WorkOrder assignWorkOrder(
            Long complaintId,
            String assigneeId,
            String priority,
            LocalDateTime deadline) {

        if (priority == null || priority.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Priority is required");
        }

        String normalizedPriority = priority.trim().toUpperCase(Locale.ROOT);

        if (!List.of("LOW", "MEDIUM", "HIGH", "URGENT").contains(normalizedPriority)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Invalid priority");
        }

        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Complaint not found"));

        if (workOrderRepository.existsByComplaint(complaint)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Work order already exists for this complaint");
        }

        User assignee = userRepository.findById(assigneeId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Assignee not found"));

        if (assignee.getRole() != Role.ADMIN) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Complaint can only be assigned to an admin");
        }

        Status oldStatus = complaint.getStatus();

        complaintTransitionService.validateTransition(
                oldStatus, Status.ASSIGNED);

        complaint.setStatus(Status.ASSIGNED);
        complaint.setAssignedTo(assignee.getEmail());
        complaintRepository.save(complaint);

        ComplaintHistory history = new ComplaintHistory();
        history.setComplaint(complaint);
        history.setActor(assignee);
        history.setOldStatus(oldStatus);
        history.setNewStatus(Status.ASSIGNED);
        history.setReason("Work order assigned");

        complaintHistoryRepository.save(history);

        WorkOrder workOrder = new WorkOrder();
        workOrder.setComplaint(complaint);
        workOrder.setAssignee(assignee);
        workOrder.setPriority(normalizedPriority);
        workOrder.setStatus(WorkOrder.STATUS_ASSIGNED);
        workOrder.setDeadline(deadline);

        return workOrderRepository.save(workOrder);
    }

    @Transactional(readOnly = true)
    public List<WorkOrder> getAllWorkOrders() {
        return workOrderRepository.findAll();
    }

    @Transactional(readOnly = true)
    public WorkOrder getWorkOrder(UUID id) {
        return workOrderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Work order not found"));
    }

    @Transactional(readOnly = true)
    public List<WorkOrder> getWorkOrdersByAssignee(String assigneeId) {
        return workOrderRepository.findByAssignee_Id(assigneeId);
    }

    /**
     * Updates work-order status and synchronizes complaint status/history.
     */
    @Transactional
    public WorkOrder updateWorkOrderStatus(
            UUID id,
            String newStatus,
            String completionNotes) {

        if (newStatus == null || newStatus.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Status is required");
        }

        String normalizedStatus = newStatus.trim().toUpperCase(Locale.ROOT);

        WorkOrder workOrder = workOrderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Work order not found"));

        String currentStatus = workOrder.getStatus();
        Status newComplaintStatus;

        if ("ASSIGNED".equals(currentStatus)
                && "IN_PROGRESS".equals(normalizedStatus)) {

            newComplaintStatus = Status.IN_PROGRESS;

        } else if ("IN_PROGRESS".equals(currentStatus)
                && "COMPLETED".equals(normalizedStatus)) {

            newComplaintStatus = Status.RESOLVED;

            workOrder.setCompletionNotes(
                    completionNotes == null || completionNotes.isBlank()
                            ? null
                            : completionNotes.trim());

        } else {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid work order status transition: "
                            + currentStatus + " -> " + normalizedStatus);
        }

        // Update work-order status
        workOrder.setStatus(normalizedStatus);
        workOrderRepository.save(workOrder);

        // Update complaint status and record history
        Complaint complaint = workOrder.getComplaint();

        complaintTransitionService.changeComplaintStatus(
                complaint.getId(),
                workOrder.getAssignee().getId(),
                newComplaintStatus.name(),
                "Work order status changed to " + normalizedStatus);

        return workOrder;
    }
}

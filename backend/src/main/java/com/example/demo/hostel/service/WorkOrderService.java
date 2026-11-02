package com.example.demo.hostel.service;

import com.example.demo.hostel.dto.UpdateWorkOrderRequest;
import com.example.demo.hostel.dto.WorkOrderView;
import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.model.Role;
import com.example.demo.hostel.model.Status;
import com.example.demo.hostel.model.User;
import com.example.demo.hostel.model.WorkOrder;
import com.example.demo.hostel.model.WorkOrderPriority;
import com.example.demo.hostel.model.WorkOrderStatus;
import com.example.demo.hostel.repository.ComplaintRepository;
import com.example.demo.hostel.repository.UserRepository;
import com.example.demo.hostel.repository.WorkOrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Work orders: a staff member (or admin) taking responsibility for a complaint.
 * Complaint status follows the work order (assigned -> ASSIGNED, in progress ->
 * IN_PROGRESS, done -> RESOLVED, cancelled -> PENDING), always through
 * ComplaintTransitionService so it is logged in complaint_history and the
 * student is notified, in the same transaction.
 */
@Service
public class WorkOrderService {

    static final List<WorkOrderStatus> ACTIVE = List.of(WorkOrderStatus.OPEN, WorkOrderStatus.IN_PROGRESS);

    private final WorkOrderRepository workOrderRepository;
    private final ComplaintRepository complaintRepository;
    private final UserRepository userRepository;
    private final ComplaintTransitionService transitionService;

    public WorkOrderService(
            WorkOrderRepository workOrderRepository,
            ComplaintRepository complaintRepository,
            UserRepository userRepository,
            ComplaintTransitionService transitionService) {
        this.workOrderRepository = workOrderRepository;
        this.complaintRepository = complaintRepository;
        this.userRepository = userRepository;
        this.transitionService = transitionService;
    }

    /**
     * Assigns (or reassigns) a complaint to an in-charge: creates its active
     * work order or updates the existing one. Staff can only assign themselves,
     * and only to a complaint nobody else holds; admins can assign anyone.
     * A PENDING complaint becomes ASSIGNED.
     *
     * @param inChargeId the assignee's user id, or null for the caller
     */
    @Transactional
    public WorkOrderView assignInCharge(Long complaintId, String inChargeId, WorkOrderPriority priority,
                                        LocalDateTime deadline, String actorEmail) {
        User actor = requireStaffOrAdmin(actorEmail);
        if (priority == null) {
            throw badRequest("Priority is required");
        }
        requireFutureDeadline(deadline);

        User assignee = inChargeId == null || inChargeId.isBlank()
                ? actor
                : userRepository.findById(inChargeId).orElseThrow(() -> badRequest("Assignee not found"));
        if (!isStaffOrAdmin(assignee)) {
            throw badRequest("Complaints can only be assigned to staff or admins");
        }
        if (actor.getRole() == Role.STAFF && !assignee.getId().equals(actor.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Staff can only assign complaints to themselves");
        }

        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Complaint not found"));
        if (complaint.getStatus() == Status.RESOLVED || complaint.getStatus() == Status.REJECTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This complaint is already closed");
        }

        WorkOrder workOrder = workOrderRepository.findFirstByComplaintAndStatusIn(complaint, ACTIVE).orElse(null);
        if (workOrder != null && actor.getRole() == Role.STAFF
                && !workOrder.getAssignee().getId().equals(actor.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This complaint is already assigned to " + displayName(workOrder.getAssignee()));
        }
        if (workOrder == null) {
            workOrder = new WorkOrder();
            workOrder.setComplaint(complaint);
        }
        workOrder.setAssignee(assignee);
        workOrder.setPriority(priority);
        workOrder.setDeadline(deadline);
        workOrder = workOrderRepository.save(workOrder);

        complaint.setAssignedTo(assignee.getEmail());
        complaintRepository.save(complaint);
        if (complaint.getStatus() == Status.PENDING) {
            transitionService.changeComplaintStatus(complaintId, actor.getId(), Status.ASSIGNED.name(),
                    "Assigned to " + displayName(assignee));
        }
        return toView(workOrder);
    }

    /**
     * Updates an active work order (assignee or admin only). Status changes
     * move the complaint: IN_PROGRESS -> IN_PROGRESS, DONE -> RESOLVED (the
     * completion notes become the history reason), CANCELLED -> PENDING and
     * unassigned.
     */
    @Transactional
    public WorkOrderView updateWorkOrder(UUID workOrderId, UpdateWorkOrderRequest request, String actorEmail) {
        User actor = requireStaffOrAdmin(actorEmail);
        WorkOrder workOrder = workOrderRepository.findById(workOrderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Work order not found"));
        if (actor.getRole() != Role.ADMIN && !workOrder.getAssignee().getId().equals(actor.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the assignee or an admin can update this work order");
        }
        if (!workOrder.getStatus().isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This work order is already " + workOrder.getStatus());
        }

        if (request.priority() != null) {
            workOrder.setPriority(request.priority());
        }
        if (request.deadline() != null) {
            requireFutureDeadline(request.deadline());
            workOrder.setDeadline(request.deadline());
        }
        if (request.completionNotes() != null) {
            String notes = request.completionNotes().trim();
            workOrder.setCompletionNotes(notes.isEmpty() ? null : notes);
        }

        WorkOrderStatus next = request.status();
        if (next != null && next != workOrder.getStatus()) {
            applyStatusChange(workOrder, next, actor);
        }
        return toView(workOrderRepository.save(workOrder));
    }

    /** The caller's active queue: earliest deadline first, then most urgent. */
    @Transactional(readOnly = true)
    public List<WorkOrderView> getMyQueue(String actorEmail) {
        User actor = requireStaffOrAdmin(actorEmail);
        return workOrderRepository.findQueue(actor, ACTIVE).stream().map(WorkOrderService::toView).toList();
    }

    /** A complaint's work orders, newest first (active one included). */
    @Transactional(readOnly = true)
    public List<WorkOrderView> getForComplaint(Long complaintId, String actorEmail) {
        requireStaffOrAdmin(actorEmail);
        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Complaint not found"));
        return workOrderRepository.findByComplaintOrderByCreatedAtDesc(complaint).stream()
                .map(WorkOrderService::toView)
                .toList();
    }

    /**
     * Cancels every active work order of a staff member being removed; their
     * complaints go back to PENDING and unassigned. Runs in the caller's transaction.
     */
    @Transactional
    public int cancelActiveWorkOrders(User assignee, User actor) {
        List<WorkOrder> active = workOrderRepository.findByAssigneeAndStatusIn(assignee, ACTIVE);
        for (WorkOrder workOrder : active) {
            applyStatusChange(workOrder, WorkOrderStatus.CANCELLED, actor);
            workOrderRepository.save(workOrder);
        }
        return active.size();
    }

    private void applyStatusChange(WorkOrder workOrder, WorkOrderStatus next, User actor) {
        WorkOrderStatus current = workOrder.getStatus();
        boolean allowed = switch (next) {
            case IN_PROGRESS -> current == WorkOrderStatus.OPEN;
            case DONE, CANCELLED -> current.isActive();
            case OPEN -> false;
        };
        if (!allowed) {
            throw badRequest("A work order cannot go from " + current + " to " + next);
        }
        workOrder.setStatus(next);

        Complaint complaint = workOrder.getComplaint();
        String reason = null;
        Status complaintStatus = switch (next) {
            case IN_PROGRESS -> Status.IN_PROGRESS;
            case DONE -> {
                reason = workOrder.getCompletionNotes();
                yield Status.RESOLVED;
            }
            case CANCELLED -> {
                complaint.setAssignedTo(null);
                complaintRepository.save(complaint);
                reason = "Assignment cancelled";
                yield Status.PENDING;
            }
            case OPEN -> throw new IllegalStateException("unreachable");
        };
        if (complaint.getStatus() != complaintStatus) {
            transitionService.changeComplaintStatus(complaint.getId(), actor.getId(), complaintStatus.name(), reason);
        }
    }

    private User requireStaffOrAdmin(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (!isStaffOrAdmin(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only staff and admins can manage work orders");
        }
        return user;
    }

    private static boolean isStaffOrAdmin(User user) {
        return user.getRole() == Role.STAFF || user.getRole() == Role.ADMIN;
    }

    private static void requireFutureDeadline(LocalDateTime deadline) {
        if (deadline != null && deadline.isBefore(LocalDateTime.now())) {
            throw badRequest("The deadline cannot be in the past");
        }
    }

    private static String displayName(User user) {
        if (user.getFullName() != null && !user.getFullName().isBlank()) return user.getFullName();
        return user.getName() != null ? user.getName() : user.getEmail();
    }

    private static ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }

    static WorkOrderView toView(WorkOrder workOrder) {
        Complaint complaint = workOrder.getComplaint();
        User assignee = workOrder.getAssignee();
        return new WorkOrderView(
                workOrder.getId(),
                complaint.getId(),
                complaint.getTitle(),
                complaint.getStatus(),
                assignee.getId(),
                displayName(assignee),
                assignee.getEmail(),
                workOrder.getPriority(),
                workOrder.getStatus(),
                workOrder.getDeadline(),
                workOrder.getCompletionNotes(),
                workOrder.getCreatedAt(),
                workOrder.getUpdatedAt());
    }
}

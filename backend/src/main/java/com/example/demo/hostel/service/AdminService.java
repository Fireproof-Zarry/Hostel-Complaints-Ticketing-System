package com.example.demo.hostel.service;

import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.model.Role;
import com.example.demo.hostel.model.Status;
import com.example.demo.hostel.model.User;
import com.example.demo.hostel.model.WorkOrderPriority;
import com.example.demo.hostel.repository.ComplaintRepository;
import com.example.demo.hostel.repository.UserRepository;
import com.example.demo.hostel.specification.ComplaintSpecification;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
public class AdminService {

    private final ComplaintRepository complaintRepository;
    private final ComplaintTransitionService transitionService;
    private final UserRepository userRepository;
    private final WorkOrderService workOrderService;

    public AdminService(
            ComplaintRepository complaintRepository,
            ComplaintTransitionService transitionService,
            UserRepository userRepository,
            WorkOrderService workOrderService) {

        this.complaintRepository = complaintRepository;
        this.transitionService = transitionService;
        this.userRepository = userRepository;
        this.workOrderService = workOrderService;
    }

    /** Everyone a complaint can be assigned to: admins and hostel staff. */
    public List<User> getAdmins() {
        return userRepository.findByRoleInOrderByEmailAsc(List.of(Role.ADMIN, Role.STAFF));
    }

    /** Archived complaints, most recently updated first. */
    public List<Complaint> getArchivedComplaints() {
        return complaintRepository.findByIsArchivedTrue().stream()
                .sorted(Comparator.comparing(
                        Complaint::getUpdatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public List<Complaint> getComplaints(
            Status status,
            String category,
            String floor,
            String assignedTo,
            LocalDateTime from,
            LocalDateTime to) {

        Specification<Complaint> specification =
                ComplaintSpecification.withFilters(
                        status,
                        category,
                        floor,
                        assignedTo,
                        from,
                        to
                );

        return complaintRepository.findAll(specification);
    }

    @Transactional
    public Complaint updateStatus(Long id, Status newStatus, String actorEmail, String reason) {
        User actor = userRepository.findByEmail(actorEmail)
                .orElseThrow(() ->
                        new IllegalArgumentException("Admin user not found"));

        // Status change, its complaint_history row and the work order commit atomically
        transitionService.changeComplaintStatus(
                id,
                actor.getId(),
                newStatus.name(),
                reason
        );
        workOrderService.syncWithComplaintStatus(id, newStatus);

        return complaintRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException("Complaint not found"));
    }

    /**
     * Dashboard "Assign": creates or updates the complaint's work order through
     * WorkOrderService (history, notification, one active work order).
     * Defaults to MEDIUM priority and no deadline when not given.
     */
    public Complaint assignComplaint(Long id, String email, WorkOrderPriority priority,
                                     LocalDateTime deadline, String actorEmail) {
        User assignee = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new IllegalArgumentException("User not found"));

        workOrderService.assignInCharge(id, assignee.getId(),
                priority == null ? WorkOrderPriority.MEDIUM : priority, deadline, actorEmail);

        return complaintRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException("Complaint not found"));
    }
}
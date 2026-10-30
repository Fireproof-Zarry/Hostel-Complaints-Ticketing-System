package com.example.demo.hostel.service;

import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.model.Role;
import com.example.demo.hostel.model.Status;
import com.example.demo.hostel.model.User;
import com.example.demo.hostel.repository.ComplaintRepository;
import com.example.demo.hostel.repository.UserRepository;
import com.example.demo.hostel.specification.ComplaintSpecification;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
public class AdminService {

    private final ComplaintRepository complaintRepository;
    private final ComplaintTransitionService transitionService;
    private final UserRepository userRepository;

    public AdminService(
            ComplaintRepository complaintRepository,
            ComplaintTransitionService transitionService,
            UserRepository userRepository) {

        this.complaintRepository = complaintRepository;
        this.transitionService = transitionService;
        this.userRepository = userRepository;
    }

    public List<User> getAdmins() {
        return userRepository.findByRole(Role.ADMIN);
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

    public Complaint updateStatus(Long id, Status newStatus, String actorEmail, String reason) {
        User actor = userRepository.findByEmail(actorEmail)
                .orElseThrow(() ->
                        new IllegalArgumentException("Admin user not found"));

        // Status change and its complaint_history row commit atomically
        transitionService.changeComplaintStatus(
                id,
                actor.getId(),
                newStatus.name(),
                reason
        );

        return complaintRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException("Complaint not found"));
    }

    public Complaint assignComplaint(Long id, String email) {
        Complaint complaint = complaintRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException("Complaint not found"));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new IllegalArgumentException("User not found"));

        if (user.getRole() != Role.ADMIN) {
            throw new IllegalArgumentException(
                    "Complaint can only be assigned to an admin");
        }

        complaint.setAssignedTo(user.getEmail());

        return complaintRepository.save(complaint);
    }
}
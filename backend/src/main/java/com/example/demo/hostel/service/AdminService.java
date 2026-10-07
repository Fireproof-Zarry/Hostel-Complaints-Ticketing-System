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

    public Complaint updateStatus(Long id, Status newStatus) {
        Complaint complaint = complaintRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException("Complaint not found"));

        Status currentStatus = complaint.getStatus();

        transitionService.validateTransition(
                currentStatus,
                newStatus
        );

        complaint.setStatus(newStatus);

        return complaintRepository.save(complaint);
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
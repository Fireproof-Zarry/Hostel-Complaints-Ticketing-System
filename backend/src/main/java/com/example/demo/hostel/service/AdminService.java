package com.example.demo.hostel.service;

import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.model.Status;
import com.example.demo.hostel.repository.ComplaintRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AdminService {

    private final ComplaintRepository complaintRepository;
    
    private final ComplaintTransitionService transitionService;
    
    public AdminService(ComplaintRepository complaintRepository, ComplaintTransitionService transitionService) {
        this.complaintRepository = complaintRepository;
        this.transitionService = transitionService;
    }

    public List<Complaint> getComplaints(
            Status status,
            String category,
            LocalDateTime from,
            LocalDateTime to) {

        if (status != null && category != null && from != null && to != null) {
            return complaintRepository
                    .findByStatusAndCategoryAndCreatedAtBetween(
                            status, category, from, to);
        }

        if (status != null && category != null) {
            return complaintRepository
                    .findByStatusAndCategory(status, category);
        }

        if (status != null && from != null && to != null) {
            return complaintRepository
                    .findByStatusAndCreatedAtBetween(status, from, to);
        }

        if (category != null && from != null && to != null) {
            return complaintRepository
                    .findByCategoryAndCreatedAtBetween(category, from, to);
        }

        if (status != null) {
            return complaintRepository.findByStatus(status);
        }

        if (category != null) {
            return complaintRepository.findByCategory(category);
        }

        if (from != null && to != null) {
            return complaintRepository.findByCreatedAtBetween(from, to);
        }

        return complaintRepository.findAll();
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
}
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

    public AdminService(ComplaintRepository complaintRepository) {
        this.complaintRepository = complaintRepository;
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
}
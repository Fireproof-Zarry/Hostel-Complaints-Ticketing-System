package com.example.demo.hostel.repository;

import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.model.Status;
import com.example.demo.hostel.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ComplaintRepository extends JpaRepository<Complaint, Long> {

    // Student complaints
    List<Complaint> findByStudent(User student);

    // Admin filters
    List<Complaint> findByStatus(Status status);

    List<Complaint> findByCategory(String category);

    List<Complaint> findByCreatedAtBetween(
            LocalDateTime from,
            LocalDateTime to
    );

    List<Complaint> findByStatusAndCategory(
            Status status,
            String category
    );

    List<Complaint> findByStatusAndCreatedAtBetween(
            Status status,
            LocalDateTime from,
            LocalDateTime to
    );

    List<Complaint> findByCategoryAndCreatedAtBetween(
            String category,
            LocalDateTime from,
            LocalDateTime to
    );

    List<Complaint> findByStatusAndCategoryAndCreatedAtBetween(
            Status status,
            String category,
            LocalDateTime from,
            LocalDateTime to
    );
}
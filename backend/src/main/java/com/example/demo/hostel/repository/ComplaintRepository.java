package com.example.demo.hostel.repository;

import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.model.Status;
import com.example.demo.hostel.model.User;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ComplaintRepository
        extends JpaRepository<Complaint, Long>,
                JpaSpecificationExecutor<Complaint> {

        // Student complaints
        List<Complaint> findByStudent(User student);

        // Used to enforce the 5-complaint limit
        long countByStudentAndStatusIn(User student, List<Status> statuses);

        // Used for GET /complaints/mine
        List<Complaint> findByStudentOrderByCreatedAtDesc(User student);

        // Used to ensure a student only sees their own complaint
        Optional<Complaint> findByIdAndStudent(Long id, User student);

        // Admin filters
        List<Complaint> findByStatus(Status status);

        List<Complaint> findByCategory(String category);

        List<Complaint> findByCreatedAtBetween(
                        LocalDateTime from,
                        LocalDateTime to);

        List<Complaint> findByStatusAndCategory(
                        Status status,
                        String category);

        List<Complaint> findByStatusAndCreatedAtBetween(
                        Status status,
                        LocalDateTime from,
                        LocalDateTime to);

        List<Complaint> findByCategoryAndCreatedAtBetween(
                        String category,
                        LocalDateTime from,
                        LocalDateTime to);

        List<Complaint> findByStatusAndCategoryAndCreatedAtBetween(
                        Status status,
                        String category,
                        LocalDateTime from,
                        LocalDateTime to);
}
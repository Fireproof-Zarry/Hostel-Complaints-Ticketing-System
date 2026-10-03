package com.example.demo.hostel.repository;

import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.model.Status;
import com.example.demo.hostel.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ComplaintRepository extends JpaRepository<Complaint, Long> {
    // Used to enforce the 5-complaint limit
    long countByStudentAndStatusIn(User student, List<Status> statuses);

    // Used for GET /complaints/mine
    List<Complaint> findByStudentOrderByCreatedAtDesc(User student);
    
    // Used to ensure a student only sees their own complaint
    Optional<Complaint> findByIdAndStudent(Long id, User student);
}
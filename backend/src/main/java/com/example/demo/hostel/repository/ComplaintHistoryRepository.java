package com.example.demo.hostel.repository;

import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.model.ComplaintHistory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ComplaintHistoryRepository extends JpaRepository<ComplaintHistory, UUID> {

        List<ComplaintHistory> findByComplaintOrderByChangedAtAsc(Complaint complaint);
}

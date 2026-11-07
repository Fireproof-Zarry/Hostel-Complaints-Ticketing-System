package com.example.demo.hostel.repository;

import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.model.ComplaintHistory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** The status change log. */
@Repository
public interface ComplaintHistoryRepository extends JpaRepository<ComplaintHistory, UUID> {

        /** Oldest first, for reading a complaint's changes in order. */
        List<ComplaintHistory> findByComplaintOrderByChangedAtAsc(Complaint complaint);

        /** Newest first, as returned by the history endpoint. */
        List<ComplaintHistory> findByComplaintOrderByChangedAtDesc(Complaint complaint);
}

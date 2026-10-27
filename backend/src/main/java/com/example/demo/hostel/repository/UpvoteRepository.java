package com.example.demo.hostel.repository;

import com.example.demo.hostel.model.Upvote;
import com.example.demo.hostel.model.UpvoteId;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UpvoteRepository extends JpaRepository<Upvote, UpvoteId> {

        long countByIdComplaintId(Long complaintId);
}

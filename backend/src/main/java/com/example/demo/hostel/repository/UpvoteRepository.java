package com.example.demo.hostel.repository;

import com.example.demo.hostel.model.Upvote;
import com.example.demo.hostel.model.UpvoteId;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UpvoteRepository extends JpaRepository<Upvote, UpvoteId> {

        long countByIdComplaintId(Long complaintId);

        boolean existsById(UpvoteId id);

        /**
         * Removes the upvote and returns the number of rows deleted (0 or 1).
         * Unlike delete(entity), deleting a row another request already
         * removed is not an error, so concurrent "remove" clicks are safe.
         */
        @Modifying(flushAutomatically = true, clearAutomatically = true)
        @Query("DELETE FROM Upvote u WHERE u.id = :id")
        int deleteUpvote(@Param("id") UpvoteId id);
}

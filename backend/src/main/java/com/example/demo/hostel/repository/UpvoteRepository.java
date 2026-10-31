package com.example.demo.hostel.repository;

import com.example.demo.hostel.model.Upvote;
import com.example.demo.hostel.model.UpvoteId;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface UpvoteRepository extends JpaRepository<Upvote, UpvoteId> {

        long countByIdComplaintId(Long complaintId);

        boolean existsById(UpvoteId id);

        /** Rows of [complaintId, upvoteCount] for the given complaints (only those with upvotes). */
        @Query("SELECT u.id.complaintId, COUNT(u) FROM Upvote u WHERE u.id.complaintId IN :complaintIds GROUP BY u.id.complaintId")
        List<Object[]> countByComplaintIds(@Param("complaintIds") Collection<Long> complaintIds);

        /** Which of the given complaints this user has upvoted. */
        @Query("SELECT u.id.complaintId FROM Upvote u WHERE u.id.userId = :userId AND u.id.complaintId IN :complaintIds")
        List<Long> findUpvotedComplaintIds(@Param("userId") String userId, @Param("complaintIds") Collection<Long> complaintIds);

        /**
         * Removes the upvote and returns the number of rows deleted (0 or 1).
         * Unlike delete(entity), deleting a row another request already
         * removed is not an error, so concurrent "remove" clicks are safe.
         */
        @Modifying(flushAutomatically = true, clearAutomatically = true)
        @Query("DELETE FROM Upvote u WHERE u.id = :id")
        int deleteUpvote(@Param("id") UpvoteId id);
}

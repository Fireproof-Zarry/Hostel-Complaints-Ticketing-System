package com.example.demo.hostel.repository;

import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.model.Status;
import com.example.demo.hostel.model.User;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ComplaintRepository
        extends JpaRepository<Complaint, Long>,
                JpaSpecificationExecutor<Complaint> {

        // Used to enforce the 5-complaint limit
        long countByStudentAndStatusIn(User student, List<Status> statuses);

        // Used for GET /complaints/mine (students see their archived complaints too)
        List<Complaint> findByStudentOrderByCreatedAtDesc(User student);

        // Used to ensure a student only sees their own complaint
        Optional<Complaint> findByIdAndStudent(Long id, User student);

        // Admin dashboard queries go through ComplaintSpecification, which
        // excludes archived complaints

        // Admin audit view of the archive
        List<Complaint> findByIsArchivedTrue();

        /**
         * Archives complaints in the given statuses last updated before the
         * cutoff, in a single UPDATE. Bypasses @UpdateTimestamp on purpose so
         * updated_at keeps showing when the complaint was resolved.
         */
        @Modifying(flushAutomatically = true, clearAutomatically = true)
        @Query("""
                UPDATE Complaint c SET c.isArchived = true
                WHERE c.isArchived = false
                  AND c.status IN :statuses
                  AND COALESCE(c.updatedAt, c.createdAt) < :cutoff
                """)
        int archiveCompletedBefore(
                        @Param("statuses") Collection<Status> statuses,
                        @Param("cutoff") LocalDateTime cutoff);
}

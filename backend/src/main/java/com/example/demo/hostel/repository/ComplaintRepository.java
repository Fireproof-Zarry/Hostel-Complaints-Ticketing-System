package com.example.demo.hostel.repository;

import com.example.demo.hostel.dto.CommunityComplaintView;
import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.model.Status;
import com.example.demo.hostel.model.User;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
         * Community dashboard, most upvoted first (newest first on ties). One
         * query: upvotes are counted with a LEFT JOIN + GROUP BY (COUNT of a single
         * column: COUNT(u) on the composite key would count the LEFT JOIN's
         * (NULL, NULL) row as one upvote), and whether
         * {@code userId} reported or upvoted each complaint is computed inline.
         * {@code floor} null means all floors.
         */
        @Query(value = """
                SELECT new com.example.demo.hostel.dto.CommunityComplaintView(
                    c.id, c.title, c.description, c.status, cat.name, l.floor, l.wing,
                    c.imageUrl, c.createdAt,
                    COUNT(u.id.userId),
                    CASE WHEN s.id = :userId THEN true ELSE false END,
                    CASE WHEN SUM(CASE WHEN u.id.userId = :userId THEN 1 ELSE 0 END) > 0 THEN true ELSE false END)
                FROM Complaint c
                JOIN c.category cat
                JOIN c.location l
                JOIN c.student s
                LEFT JOIN Upvote u ON u.complaint = c
                WHERE l.communityArea = true
                  AND c.isArchived = false
                  AND c.status IN :statuses
                  AND (:floor IS NULL OR l.floor = :floor)
                GROUP BY c.id, c.title, c.description, c.status, cat.name, l.floor, l.wing,
                         c.imageUrl, c.createdAt, s.id
                ORDER BY COUNT(u.id.userId) DESC, c.createdAt DESC
                """, countQuery = """
                SELECT COUNT(c)
                FROM Complaint c
                JOIN c.location l
                WHERE l.communityArea = true
                  AND c.isArchived = false
                  AND c.status IN :statuses
                  AND (:floor IS NULL OR l.floor = :floor)
                """)
        Page<CommunityComplaintView> findCommunityComplaintsByUpvotes(
                        @Param("statuses") Collection<Status> statuses,
                        @Param("floor") String floor,
                        @Param("userId") String userId,
                        Pageable pageable);

        /** Same as findCommunityComplaintsByUpvotes, newest first. */
        @Query(value = """
                SELECT new com.example.demo.hostel.dto.CommunityComplaintView(
                    c.id, c.title, c.description, c.status, cat.name, l.floor, l.wing,
                    c.imageUrl, c.createdAt,
                    COUNT(u.id.userId),
                    CASE WHEN s.id = :userId THEN true ELSE false END,
                    CASE WHEN SUM(CASE WHEN u.id.userId = :userId THEN 1 ELSE 0 END) > 0 THEN true ELSE false END)
                FROM Complaint c
                JOIN c.category cat
                JOIN c.location l
                JOIN c.student s
                LEFT JOIN Upvote u ON u.complaint = c
                WHERE l.communityArea = true
                  AND c.isArchived = false
                  AND c.status IN :statuses
                  AND (:floor IS NULL OR l.floor = :floor)
                GROUP BY c.id, c.title, c.description, c.status, cat.name, l.floor, l.wing,
                         c.imageUrl, c.createdAt, s.id
                ORDER BY c.createdAt DESC
                """, countQuery = """
                SELECT COUNT(c)
                FROM Complaint c
                JOIN c.location l
                WHERE l.communityArea = true
                  AND c.isArchived = false
                  AND c.status IN :statuses
                  AND (:floor IS NULL OR l.floor = :floor)
                """)
        Page<CommunityComplaintView> findCommunityComplaintsByRecent(
                        @Param("statuses") Collection<Status> statuses,
                        @Param("floor") String floor,
                        @Param("userId") String userId,
                        Pageable pageable);

        /**
         * Sets the pg_trgm thresholds used by the % and <% operators for the
         * current transaction only (set_config(..., true) is transaction-local).
         */
        @Query(value = """
                SELECT set_config('pg_trgm.similarity_threshold', :threshold, true)
                    || set_config('pg_trgm.word_similarity_threshold', :threshold, true)
                """, nativeQuery = true)
        String setTrigramThresholds(@Param("threshold") String threshold);

        /**
         * IDs of up to 3 open, non-archived community-area complaints on the
         * given floor whose title is similar to {@code title} (%) or whose
         * description contains words similar to it (<%), most similar first;
         * on equal scores, complaints in the same wing come first. Both
         * operators are backed by the GIN trigram indexes in schema_oct25.sql.
         * Requires PostgreSQL with pg_trgm; call setTrigramThresholds first.
         */
        @Query(value = """
                SELECT c.id
                FROM complaints c
                JOIN locations l ON l.location_id = c.location_id
                WHERE l.is_community_area = true
                  AND c.is_archived = false
                  AND c.status IN ('PENDING', 'ASSIGNED', 'IN_PROGRESS')
                  AND l.floor = :floor
                  AND (c.title % :title OR :title <% c.description)
                ORDER BY GREATEST(similarity(c.title, :title), word_similarity(:title, c.description)) DESC,
                         CASE WHEN lower(l.wing) = lower(:wing) THEN 0 ELSE 1 END,
                         c.created_at DESC
                LIMIT 3
                """, nativeQuery = true)
        List<Long> findSimilarOpenCommunityComplaintIds(
                        @Param("title") String title,
                        @Param("floor") String floor,
                        @Param("wing") String wing);

        /**
         * Same search for personal complaints in one room (floor + room number),
         * e.g. roommates in a multiple-occupancy room reporting the same issue.
         */
        @Query(value = """
                SELECT c.id
                FROM complaints c
                JOIN locations l ON l.location_id = c.location_id
                WHERE l.is_community_area = false
                  AND c.is_archived = false
                  AND c.status IN ('PENDING', 'ASSIGNED', 'IN_PROGRESS')
                  AND l.floor = :floor
                  AND l.room_number = :room
                  AND (c.title % :title OR :title <% c.description)
                ORDER BY GREATEST(similarity(c.title, :title), word_similarity(:title, c.description)) DESC,
                         c.created_at DESC
                LIMIT 3
                """, nativeQuery = true)
        List<Long> findSimilarOpenRoomComplaintIds(
                        @Param("title") String title,
                        @Param("floor") String floor,
                        @Param("room") String room);

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

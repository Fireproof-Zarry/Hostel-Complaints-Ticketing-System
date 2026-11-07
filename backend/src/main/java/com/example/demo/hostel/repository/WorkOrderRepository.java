package com.example.demo.hostel.repository;

import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.model.User;
import com.example.demo.hostel.model.WorkOrder;
import com.example.demo.hostel.model.WorkOrderStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Work order queries. Queries by assignee skip soft-deleted complaints. */
@Repository
public interface WorkOrderRepository extends JpaRepository<WorkOrder, UUID> {

        /** A complaint's work orders, newest first (its assignment history). */
        List<WorkOrder> findByComplaintOrderByCreatedAtDesc(Complaint complaint);

        /** The complaint's active work order, if any (at most one, see schema_nov03.sql). */
        Optional<WorkOrder> findFirstByComplaintAndStatusIn(Complaint complaint, Collection<WorkOrderStatus> statuses);

        /** The given complaints' work orders in these statuses, with their assignees. */
        @Query("""
                SELECT w FROM WorkOrder w JOIN FETCH w.assignee
                WHERE w.complaint IN :complaints AND w.status IN :statuses
                """)
        List<WorkOrder> findWithAssigneeByComplaints(
                        @Param("complaints") Collection<Complaint> complaints,
                        @Param("statuses") Collection<WorkOrderStatus> statuses);

        /**
         * All of an assignee's work orders in the given statuses (e.g. to cancel
         * when removing staff). Skips soft-deleted complaints, which can no longer
         * be loaded through a work order.
         */
        @Query("""
                SELECT w FROM WorkOrder w
                WHERE w.assignee = :assignee AND w.status IN :statuses
                  AND w.complaint.deletedAt IS NULL
                """)
        List<WorkOrder> findByAssigneeAndStatusIn(
                        @Param("assignee") User assignee,
                        @Param("statuses") Collection<WorkOrderStatus> statuses);

        /**
         * An in-charge's oversight queue: earliest deadline first (no deadline
         * last), then most urgent first, then oldest first. Skips soft-deleted
         * complaints.
         */
        @Query("""
                SELECT w FROM WorkOrder w
                WHERE w.assignee = :assignee AND w.status IN :statuses
                  AND w.complaint.deletedAt IS NULL
                ORDER BY w.deadline ASC NULLS LAST,
                         CASE w.priority
                             WHEN com.example.demo.hostel.model.WorkOrderPriority.URGENT THEN 0
                             WHEN com.example.demo.hostel.model.WorkOrderPriority.HIGH THEN 1
                             WHEN com.example.demo.hostel.model.WorkOrderPriority.MEDIUM THEN 2
                             ELSE 3
                         END,
                         w.createdAt ASC
                """)
        List<WorkOrder> findQueue(
                        @Param("assignee") User assignee,
                        @Param("statuses") Collection<WorkOrderStatus> statuses);
}

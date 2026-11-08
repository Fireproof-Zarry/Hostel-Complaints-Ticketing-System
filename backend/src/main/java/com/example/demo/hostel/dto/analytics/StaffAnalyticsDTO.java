package com.example.demo.hostel.dto.analytics;

import java.util.List;

/**
 * Work order figures for the Staff tab. active and overdue are as of now; the rest
 * use the date range (assigned: when created; completed and cancelled: when finished).
 * Averages and rates are null when there is nothing to measure.
 *
 * @param onTimeRate percentage of completed work orders with a deadline that were
 *                   done by it, in hostel (IST) time
 */
public record StaffAnalyticsDTO(
        long activeWorkOrders,
        long overdueWorkOrders,
        long completedWorkOrders,
        Double averageCompletionTimeHours,
        Double onTimeRate,
        List<Member> members
) {

    /**
     * One assignee's work orders. role is STAFF, ADMIN, or STUDENT for someone who
     * has since been removed from staff.
     */
    public record Member(
            String userId,
            String name,
            String email,
            String role,
            long assigned,
            long active,
            long overdue,
            long completed,
            long cancelled,
            Double averageCompletionTimeHours,
            Double onTimeRate
    ) {
    }
}

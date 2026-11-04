
package com.example.demo.hostel.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface AnalyticsRepository
        extends Repository<com.example.demo.hostel.model.Complaint, Long> {

    /*
     * Projection interfaces
     */

    interface StatusCount {
        String getStatus();

        Long getCount();
    }

    interface CategoryCount {
        String getCategory();

        Long getCount();
    }

    interface FloorCount {
        String getFloor();

        Long getCount();
    }

    interface RecurringIssue {
        String getType();

        String getFloor();

        String getRoomNumber();

        String getWing();

        String getCategory();

        Long getCount();
    }

    interface MonthlyCount {
        String getMonth();

        Long getCount();
    }

    /*
     * 1. Overall complaint counts grouped by status.
     * Date filter: complaint creation date.
     * Includes archived complaints.
     * Excludes soft-deleted complaints.
     */

    @Query(value = """
            SELECT
                c.status AS status,
                COUNT(c.id) AS count
            FROM complaints c
            WHERE c.deleted_at IS NULL
              AND (CAST(:startDate AS date) IS NULL
                   OR c.created_at >= CAST(:startDate AS date))
              AND (CAST(:endDate AS date) IS NULL
                   OR c.created_at < CAST(:endDate AS date) + INTERVAL '1 day')
            GROUP BY c.status
            """, nativeQuery = true)
    List<StatusCount> countComplaintsByStatus(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /*
     * 2. Category-wise complaint counts.
     * Includes categories with zero complaints.
     * Date filter: complaint creation date.
     */

    @Query(value = """
            SELECT
                cat.name AS category,
                COUNT(c.id) AS count
            FROM categories cat
            LEFT JOIN complaints c
                ON c.category_id = cat.category_id
                AND c.deleted_at IS NULL
                AND (CAST(:startDate AS date) IS NULL
                     OR c.created_at >= CAST(:startDate AS date))
                AND (CAST(:endDate AS date) IS NULL
                     OR c.created_at < CAST(:endDate AS date) + INTERVAL '1 day')
            GROUP BY cat.category_id, cat.name
            ORDER BY cat.name
            """, nativeQuery = true)
    List<CategoryCount> countComplaintsByCategory(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /*
     * 3. Floor-wise complaint counts.
     * Includes every configured floor, even if it has zero complaints.
     * Date filter: complaint creation date.
     */

    @Query(value = """
            SELECT
                floors.floor AS floor,
                COUNT(c.id) AS count
            FROM (
                SELECT DISTINCT floor
                FROM locations
            ) floors
            LEFT JOIN locations l
                ON l.floor = floors.floor
            LEFT JOIN complaints c
                ON c.location_id = l.location_id
                AND c.deleted_at IS NULL
                AND (CAST(:startDate AS date) IS NULL
                     OR c.created_at >= CAST(:startDate AS date))
                AND (CAST(:endDate AS date) IS NULL
                     OR c.created_at < CAST(:endDate AS date) + INTERVAL '1 day')
            GROUP BY floors.floor
            ORDER BY floors.floor
            """, nativeQuery = true)
    List<FloorCount> countComplaintsByFloor(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /*
     * 4. Recurring room and community-area issues.
     *
     * Room grouping: floor + room number + category.
     * Community grouping: floor + wing + category.
     * Null community wings become UNSPECIFIED.
     * Only groups with at least 2 complaints are returned.
     */

    @Query(value = """
            SELECT *
            FROM (
                SELECT
                    'ROOM' AS type,
                    l.floor AS floor,
                    l.room_number AS "roomNumber",
                    NULL::varchar AS wing,
                    cat.name AS category,
                    COUNT(c.id) AS count
                FROM complaints c
                JOIN locations l
                    ON l.location_id = c.location_id
                JOIN categories cat
                    ON cat.category_id = c.category_id
                WHERE c.deleted_at IS NULL
                  AND l.is_community_area = false
                  AND l.room_number IS NOT NULL
                  AND (CAST(:startDate AS date) IS NULL
                       OR c.created_at >= CAST(:startDate AS date))
                  AND (CAST(:endDate AS date) IS NULL
                       OR c.created_at < CAST(:endDate AS date) + INTERVAL '1 day')
                GROUP BY l.floor, l.room_number, cat.name
                HAVING COUNT(c.id) >= 2

                UNION ALL

                SELECT
                    'COMMUNITY_AREA' AS type,
                    l.floor AS floor,
                    NULL::varchar AS "roomNumber",
                    COALESCE(l.wing, 'UNSPECIFIED') AS wing,
                    cat.name AS category,
                    COUNT(c.id) AS count
                FROM complaints c
                JOIN locations l
                    ON l.location_id = c.location_id
                JOIN categories cat
                    ON cat.category_id = c.category_id
                WHERE c.deleted_at IS NULL
                  AND l.is_community_area = true
                  AND (CAST(:startDate AS date) IS NULL
                       OR c.created_at >= CAST(:startDate AS date))
                  AND (CAST(:endDate AS date) IS NULL
                       OR c.created_at < CAST(:endDate AS date) + INTERVAL '1 day')
                GROUP BY l.floor, COALESCE(l.wing, 'UNSPECIFIED'), cat.name
                HAVING COUNT(c.id) >= 2
            ) recurring
            ORDER BY count DESC, floor, type, category
            """, nativeQuery = true)
    List<RecurringIssue> findRecurringIssues(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /*
     * 5. Monthly complaint creation counts.
     * Returns only months with activity.
     * AnalyticsService fills missing months with zero counts.
     */

    @Query(value = """
            SELECT
                TO_CHAR(DATE_TRUNC('month', c.created_at), 'YYYY-MM') AS month,
                COUNT(c.id) AS count
            FROM complaints c
            WHERE c.deleted_at IS NULL
              AND (CAST(:startDate AS date) IS NULL
                   OR c.created_at >= CAST(:startDate AS date))
              AND (CAST(:endDate AS date) IS NULL
                   OR c.created_at < CAST(:endDate AS date) + INTERVAL '1 day')
            GROUP BY DATE_TRUNC('month', c.created_at)
            ORDER BY DATE_TRUNC('month', c.created_at)
            """, nativeQuery = true)
    List<MonthlyCount> countComplaintsCreatedByMonth(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /*
     * 6. Monthly resolved complaint counts.
     * Uses the latest transition to RESOLVED for each complaint.
     * Includes complaints subsequently reopened.
     */

    @Query(value = """
            WITH latest_resolution AS (
                SELECT
                    complaint_id,
                    MAX(changed_at) AS latest_resolved_at
                FROM complaint_history
                WHERE new_status = 'RESOLVED'
                GROUP BY complaint_id
            )
            SELECT
                TO_CHAR(DATE_TRUNC('month', lr.latest_resolved_at), 'YYYY-MM') AS month,
                COUNT(c.id) AS count
            FROM latest_resolution lr
            JOIN complaints c
                ON c.id = lr.complaint_id
            WHERE c.deleted_at IS NULL
              AND (CAST(:startDate AS date) IS NULL
                   OR lr.latest_resolved_at >= CAST(:startDate AS date))
              AND (CAST(:endDate AS date) IS NULL
                   OR lr.latest_resolved_at < CAST(:endDate AS date) + INTERVAL '1 day')
            GROUP BY DATE_TRUNC('month', lr.latest_resolved_at)
            ORDER BY DATE_TRUNC('month', lr.latest_resolved_at)
            """, nativeQuery = true)
    List<MonthlyCount> countComplaintsResolvedByMonth(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /*
     * 7. Average resolution time in hours.
     * Only currently RESOLVED complaints are included.
     * Uses the latest transition to RESOLVED.
     * Returns null when no matching complaints exist.
     */

    @Query(value = """
            WITH latest_resolution AS (
                SELECT
                    complaint_id,
                    MAX(changed_at) AS latest_resolved_at
                FROM complaint_history
                WHERE new_status = 'RESOLVED'
                GROUP BY complaint_id
            )
            SELECT
                AVG(
                    EXTRACT(EPOCH FROM (lr.latest_resolved_at - c.created_at)) / 3600.0
                )::double precision
            FROM complaints c
            JOIN latest_resolution lr
                ON lr.complaint_id = c.id
            WHERE c.deleted_at IS NULL
              AND c.status = 'RESOLVED'
              AND (CAST(:startDate AS date) IS NULL
                   OR lr.latest_resolved_at >= CAST(:startDate AS date))
              AND (CAST(:endDate AS date) IS NULL
                   OR lr.latest_resolved_at < CAST(:endDate AS date) + INTERVAL '1 day')
            """, nativeQuery = true)
    Double findAverageResolutionTimeHours(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}

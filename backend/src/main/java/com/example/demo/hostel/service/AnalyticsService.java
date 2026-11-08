package com.example.demo.hostel.service;

import com.example.demo.hostel.dto.analytics.AnalyticsResponseDTO;
import com.example.demo.hostel.dto.analytics.OverviewDTO;
import com.example.demo.hostel.dto.analytics.CategoryAnalyticsDTO;
import com.example.demo.hostel.dto.analytics.FloorAnalyticsDTO;
import com.example.demo.hostel.dto.analytics.RecurringIssueDTO;
import com.example.demo.hostel.dto.analytics.MonthlyTrendDTO;
import com.example.demo.hostel.dto.analytics.StaffAnalyticsDTO;
import com.example.demo.hostel.repository.AnalyticsRepository;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Turns the queries in AnalyticsRepository into the dashboard's figures. The date range
 * applies to when complaints were filed, except that resolution figures use when they
 * were resolved, and staff figures are explained on StaffAnalyticsDTO. Soft-deleted
 * complaints are never counted.
 */
@Service
public class AnalyticsService {

    private final AnalyticsRepository analyticsRepository;

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    public AnalyticsService(AnalyticsRepository analyticsRepository) {
        this.analyticsRepository = analyticsRepository;
    }

    /**
     * Fetches all analytics data and constructs the response DTO.
     *
     * @throws IllegalArgumentException if startDate is after endDate
     */
    public AnalyticsResponseDTO getAnalytics(
            LocalDate startDate,
            LocalDate endDate) {

        if (startDate != null && endDate != null
                && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException(
                    "Start date cannot be after end date");
        }

        List<AnalyticsRepository.StatusCount> statusCounts = analyticsRepository.countComplaintsByStatus(
                startDate, endDate);

        Double averageResolutionTime = analyticsRepository.findAverageResolutionTimeHours(
                startDate, endDate);

        AnalyticsResponseDTO response = new AnalyticsResponseDTO();

        response.setOverview(
                buildOverview(statusCounts, averageResolutionTime));

        response.setCategoryBreakdown(
                buildCategoryBreakdown(startDate, endDate));

        response.setFloorBreakdown(
                buildFloorBreakdown(startDate, endDate));

        response.setRecurringIssues(
                buildRecurringIssues(startDate, endDate));

        response.setMonthlyTrends(
                buildMonthlyTrends(startDate, endDate));

        response.setStaff(
                buildStaffAnalytics(startDate, endDate));

        return response;
    }

    /*
     * 1. Overview
     *
     * Calculates:
     * - Total complaints
     * - Counts by status
     * - Unresolved complaints
     * - Resolution rate
     * - Average resolution time
     */
    private OverviewDTO buildOverview(
            List<AnalyticsRepository.StatusCount> statusCounts,
            Double averageResolutionTime) {

        Map<String, Long> counts = new HashMap<>();

        for (AnalyticsRepository.StatusCount result : statusCounts) {
            counts.put(
                    result.getStatus(),
                    result.getCount() == null ? 0L : result.getCount());
        }

        long pending = counts.getOrDefault("PENDING", 0L);
        long assigned = counts.getOrDefault("ASSIGNED", 0L);
        long inProgress = counts.getOrDefault("IN_PROGRESS", 0L);
        long resolved = counts.getOrDefault("RESOLVED", 0L);
        long rejected = counts.getOrDefault("REJECTED", 0L);

        long total = pending + assigned + inProgress
                + resolved + rejected;

        long unresolved = pending + assigned + inProgress;

        long eligibleComplaints = total - rejected;

        double resolutionRate = eligibleComplaints == 0
                ? 0.0
                : (resolved * 100.0) / eligibleComplaints;

        OverviewDTO overview = new OverviewDTO();

        overview.setTotalComplaints(total);
        overview.setPending(pending);
        overview.setAssigned(assigned);
        overview.setInProgress(inProgress);
        overview.setResolved(resolved);
        overview.setRejected(rejected);
        overview.setUnresolved(unresolved);
        overview.setResolutionRate(
                Math.round(resolutionRate * 100.0) / 100.0);
        overview.setAverageResolutionTimeHours(averageResolutionTime);

        return overview;
    }

    /*
     * 2. Category-wise breakdown
     *
     * Repository already includes categories with zero complaints.
     */
    private List<CategoryAnalyticsDTO> buildCategoryBreakdown(
            LocalDate startDate,
            LocalDate endDate) {

        List<AnalyticsRepository.CategoryCount> results = analyticsRepository.countComplaintsByCategory(
                startDate, endDate);

        List<CategoryAnalyticsDTO> breakdown = new ArrayList<>();

        for (AnalyticsRepository.CategoryCount result : results) {
            CategoryAnalyticsDTO dto = new CategoryAnalyticsDTO();

            dto.setCategory(result.getCategory());
            dto.setCount(result.getCount() == null
                    ? 0L
                    : result.getCount());

            breakdown.add(dto);
        }

        return breakdown;
    }

    /*
     * 3. Floor-wise breakdown
     *
     * Repository already includes floors with zero complaints.
     */
    private List<FloorAnalyticsDTO> buildFloorBreakdown(
            LocalDate startDate,
            LocalDate endDate) {

        List<AnalyticsRepository.FloorCount> results = analyticsRepository.countComplaintsByFloor(
                startDate, endDate);

        List<FloorAnalyticsDTO> breakdown = new ArrayList<>();

        for (AnalyticsRepository.FloorCount result : results) {
            FloorAnalyticsDTO dto = new FloorAnalyticsDTO();

            dto.setFloor(result.getFloor());
            dto.setCount(result.getCount() == null
                    ? 0L
                    : result.getCount());

            breakdown.add(dto);
        }

        return breakdown;
    }

    /*
     * 4. Recurring room and community-area issues
     *
     * Repository handles grouping and minimum count of 2.
     */
    private List<RecurringIssueDTO> buildRecurringIssues(
            LocalDate startDate,
            LocalDate endDate) {

        List<AnalyticsRepository.RecurringIssue> results = analyticsRepository.findRecurringIssues(
                startDate, endDate);

        List<RecurringIssueDTO> recurringIssues = new ArrayList<>();

        for (AnalyticsRepository.RecurringIssue result : results) {
            RecurringIssueDTO dto = new RecurringIssueDTO();

            dto.setType(result.getType());
            dto.setFloor(result.getFloor());
            dto.setRoomNumber(result.getRoomNumber());
            dto.setWing(result.getWing());
            dto.setCategory(result.getCategory());
            dto.setCount(result.getCount() == null
                    ? 0L
                    : result.getCount());

            recurringIssues.add(dto);
        }

        return recurringIssues;
    }

    /*
     * 5. Monthly trends
     *
     * Combines:
     * - Monthly created complaints
     * - Monthly resolved complaints
     *
     * Fills missing months with zero counts.
     */

    private List<MonthlyTrendDTO> buildMonthlyTrends(
            LocalDate startDate,
            LocalDate endDate) {

        List<AnalyticsRepository.MonthlyCount> createdResults = analyticsRepository.countComplaintsCreatedByMonth(
                startDate, endDate);

        List<AnalyticsRepository.MonthlyCount> resolvedResults = analyticsRepository.countComplaintsResolvedByMonth(
                startDate, endDate);

        // Generate the exact months required for the selected date range.
        // TreeMap maintains chronological order.
        Map<YearMonth, MonthlyTrendDTO> monthlyData = new TreeMap<>();

        for (YearMonth month : generateMonths(startDate, endDate)) {
            monthlyData.put(month, createMonthlyDTO(month));
        }

        // Add created complaint counts only for months in the required range.
        for (AnalyticsRepository.MonthlyCount result : createdResults) {

            YearMonth month = YearMonth.parse(result.getMonth());

            MonthlyTrendDTO dto = monthlyData.get(month);

            if (dto != null) {
                dto.setCreated(result.getCount() == null
                        ? 0L
                        : result.getCount());
            }
        }

        // Add resolved complaint counts only for months in the required range.
        for (AnalyticsRepository.MonthlyCount result : resolvedResults) {

            YearMonth month = YearMonth.parse(result.getMonth());

            MonthlyTrendDTO dto = monthlyData.get(month);

            if (dto != null) {
                dto.setResolved(result.getCount() == null
                        ? 0L
                        : result.getCount());
            }
        }

        return new ArrayList<>(monthlyData.values());
    }

    /*
     * 6. Staff work orders
     *
     * One row per current staff member, plus any admin or former staff member
     * with work orders in the result. Overdue and on-time use hostel (IST) time,
     * because deadlines are picked as IST wall-clock time.
     */
    private StaffAnalyticsDTO buildStaffAnalytics(
            LocalDate startDate,
            LocalDate endDate) {

        Map<String, StaffTally> tallies = new LinkedHashMap<>();

        for (AnalyticsRepository.StaffMember member : analyticsRepository.findStaffMembers()) {
            tallies.put(member.getUserId(), new StaffTally(member));
        }

        StaffTally total = new StaffTally(null);
        LocalDateTime hostelNow = LocalDateTime.now(WorkOrderService.HOSTEL_ZONE);

        for (AnalyticsRepository.StaffWorkOrder workOrder
                : analyticsRepository.findStaffWorkOrders(startDate, endDate)) {

            StaffTally tally = tallies.computeIfAbsent(
                    workOrder.getUserId(), id -> new StaffTally(workOrder));

            tally.add(workOrder, hostelNow);
            total.add(workOrder, hostelNow);
        }

        List<StaffAnalyticsDTO.Member> members = tallies.values().stream()
                .map(StaffTally::toMember)
                .sorted(Comparator.comparing(
                        StaffAnalyticsDTO.Member::name,
                        String.CASE_INSENSITIVE_ORDER))
                .toList();

        return new StaffAnalyticsDTO(
                total.active,
                total.overdue,
                total.completed,
                total.averageCompletionTimeHours(),
                total.onTimeRate(),
                members);
    }

    /** Running counts for one assignee, or for everyone when member is null. */
    private static final class StaffTally {

        private final AnalyticsRepository.StaffMember member;

        private long assigned;
        private long active;
        private long overdue;
        private long completed;
        private long cancelled;
        private double completionHoursTotal;
        private long completedWithDeadline;
        private long completedOnTime;

        private StaffTally(AnalyticsRepository.StaffMember member) {
            this.member = member;
        }

        private void add(
                AnalyticsRepository.StaffWorkOrder workOrder,
                LocalDateTime hostelNow) {

            if (Boolean.TRUE.equals(workOrder.getCreatedInRange())) {
                assigned++;
            }

            String status = workOrder.getStatus();
            LocalDateTime deadline = workOrder.getDeadline();
            boolean finishedInRange = Boolean.TRUE.equals(workOrder.getFinishedInRange());

            if ("OPEN".equals(status) || "IN_PROGRESS".equals(status)) {
                active++;

                if (deadline != null && deadline.isBefore(hostelNow)) {
                    overdue++;
                }
            } else if ("DONE".equals(status) && finishedInRange) {
                completed++;

                LocalDateTime finishedAt = workOrder.getFinishedAt();

                completionHoursTotal += Duration.between(
                        workOrder.getCreatedAt(), finishedAt).toSeconds() / 3600.0;

                if (deadline != null) {
                    completedWithDeadline++;

                    if (!toHostelTime(finishedAt).isAfter(deadline)) {
                        completedOnTime++;
                    }
                }
            } else if ("CANCELLED".equals(status) && finishedInRange) {
                cancelled++;
            }
        }

        private Double averageCompletionTimeHours() {
            return completed == 0
                    ? null
                    : roundToTwoPlaces(completionHoursTotal / completed);
        }

        private Double onTimeRate() {
            return completedWithDeadline == 0
                    ? null
                    : roundToTwoPlaces(completedOnTime * 100.0 / completedWithDeadline);
        }

        private StaffAnalyticsDTO.Member toMember() {
            return new StaffAnalyticsDTO.Member(
                    member.getUserId(),
                    member.getName(),
                    member.getEmail(),
                    member.getRole(),
                    assigned,
                    active,
                    overdue,
                    completed,
                    cancelled,
                    averageCompletionTimeHours(),
                    onTimeRate());
        }

        // Timestamps are written in the server's zone (UTC on Render); deadlines are IST
        private static LocalDateTime toHostelTime(LocalDateTime serverTime) {
            return serverTime.atZone(ZoneId.systemDefault())
                    .withZoneSameInstant(WorkOrderService.HOSTEL_ZONE)
                    .toLocalDateTime();
        }

        private static double roundToTwoPlaces(double value) {
            return Math.round(value * 100.0) / 100.0;
        }
    }

    /*
     * Creates a monthly DTO with zero counts.
     */
    private MonthlyTrendDTO createMonthlyDTO(YearMonth month) {

        MonthlyTrendDTO dto = new MonthlyTrendDTO();

        dto.setMonth(month.format(MONTH_FORMATTER));
        dto.setCreated(0L);
        dto.setResolved(0L);

        return dto;
    }

    /*
     * 7. Generate the months required for the trend.
     *
     * No date range:
     * Most recent 12 months, including current month.
     *
     * Date range:
     * Every month from start through end, inclusive.
     *
     * If only one boundary is provided, the other boundary
     * is derived to give a useful monthly window.
     */
    private List<YearMonth> generateMonths(
            LocalDate startDate,
            LocalDate endDate) {

        YearMonth start;
        YearMonth end;

        if (startDate == null && endDate == null) {

            end = YearMonth.now();
            start = end.minusMonths(11);

        } else if (startDate != null && endDate != null) {

            start = YearMonth.from(startDate);
            end = YearMonth.from(endDate);

        } else if (startDate != null) {

            start = YearMonth.from(startDate);
            end = YearMonth.now();

        } else {

            end = YearMonth.from(endDate);
            start = end.minusMonths(11);
        }

        List<YearMonth> months = new ArrayList<>();

        for (YearMonth month = start; !month.isAfter(end); month = month.plusMonths(1)) {

            months.add(month);
        }

        return months;
    }
}
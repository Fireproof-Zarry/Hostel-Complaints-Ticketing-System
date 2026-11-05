package com.example.demo.hostel.service;

import com.example.demo.hostel.dto.analytics.AnalyticsResponseDTO;
import com.example.demo.hostel.dto.analytics.OverviewDTO;
import com.example.demo.hostel.dto.analytics.CategoryAnalyticsDTO;
import com.example.demo.hostel.dto.analytics.FloorAnalyticsDTO;
import com.example.demo.hostel.dto.analytics.RecurringIssueDTO;
import com.example.demo.hostel.dto.analytics.MonthlyTrendDTO;
import com.example.demo.hostel.repository.AnalyticsRepository;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class AnalyticsService {

    private final AnalyticsRepository analyticsRepository;

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    public AnalyticsService(AnalyticsRepository analyticsRepository) {
        this.analyticsRepository = analyticsRepository;
    }

    /*
     * Main method:
     * Fetches all analytics data and constructs the response DTO.
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
     * 6. Generate the months required for the trend.
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
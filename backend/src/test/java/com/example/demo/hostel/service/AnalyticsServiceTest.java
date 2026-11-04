
package com.example.demo.hostel.service;

import com.example.demo.hostel.dto.analytics.AnalyticsResponseDTO;
import com.example.demo.hostel.dto.analytics.MonthlyTrendDTO;
import com.example.demo.hostel.repository.AnalyticsRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private AnalyticsRepository analyticsRepository;

    @InjectMocks
    private AnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        lenient().when(analyticsRepository.countComplaintsByStatus(any(), any()))
                .thenReturn(List.of());

        lenient().when(analyticsRepository.findAverageResolutionTimeHours(any(), any()))
                .thenReturn(null);

        lenient().when(analyticsRepository.countComplaintsByCategory(any(), any()))
                .thenReturn(List.of());

        lenient().when(analyticsRepository.countComplaintsByFloor(any(), any()))
                .thenReturn(List.of());

        lenient().when(analyticsRepository.findRecurringIssues(any(), any()))
                .thenReturn(List.of());

        lenient().when(analyticsRepository.countComplaintsCreatedByMonth(any(), any()))
                .thenReturn(List.of());

        lenient().when(analyticsRepository.countComplaintsResolvedByMonth(any(), any()))
                .thenReturn(List.of());
    }

    // ---------------------------------------------------------
    // 1. Overview calculations
    // ---------------------------------------------------------

    @Test
    void getAnalytics_calculatesOverviewCorrectly() {

        doReturn(List.of(
                status("PENDING", 10),
                status("ASSIGNED", 5),
                status("IN_PROGRESS", 3),
                status("RESOLVED", 20),
                status("REJECTED", 2))).when(analyticsRepository).countComplaintsByStatus(null, null);

        doReturn(24.5)
                .when(analyticsRepository)
                .findAverageResolutionTimeHours(null, null);

        AnalyticsResponseDTO response = analyticsService.getAnalytics(null, null);

        var overview = response.getOverview();

        assertThat(overview.getTotalComplaints()).isEqualTo(40);
        assertThat(overview.getPending()).isEqualTo(10);
        assertThat(overview.getAssigned()).isEqualTo(5);
        assertThat(overview.getInProgress()).isEqualTo(3);
        assertThat(overview.getResolved()).isEqualTo(20);
        assertThat(overview.getRejected()).isEqualTo(2);

        assertThat(overview.getUnresolved()).isEqualTo(18);

        // 20 / (40 - 2) * 100 = 52.63
        assertThat(overview.getResolutionRate()).isEqualTo(52.63);

        assertThat(overview.getAverageResolutionTimeHours())
                .isEqualTo(24.5);
    }

    @Test
    void getAnalytics_returnsZeroResolutionRateWhenDenominatorIsZero() {

        doReturn(List.of(
                status("REJECTED", 5))).when(analyticsRepository).countComplaintsByStatus(null, null);

        AnalyticsResponseDTO response = analyticsService.getAnalytics(null, null);

        assertThat(response.getOverview().getTotalComplaints())
                .isEqualTo(5);

        assertThat(response.getOverview().getResolutionRate())
                .isEqualTo(0.0);
    }

    @Test
    void getAnalytics_defaultsMissingStatusesToZero() {

        doReturn(List.of(
                status("PENDING", 4))).when(analyticsRepository).countComplaintsByStatus(null, null);

        AnalyticsResponseDTO response = analyticsService.getAnalytics(null, null);

        var overview = response.getOverview();

        assertThat(overview.getTotalComplaints()).isEqualTo(4);
        assertThat(overview.getPending()).isEqualTo(4);
        assertThat(overview.getAssigned()).isZero();
        assertThat(overview.getInProgress()).isZero();
        assertThat(overview.getResolved()).isZero();
        assertThat(overview.getRejected()).isZero();
        assertThat(overview.getUnresolved()).isEqualTo(4);
        assertThat(overview.getAverageResolutionTimeHours()).isNull();
    }

    // ---------------------------------------------------------
    // 2. Category, floor and recurring issue mapping
    // ---------------------------------------------------------

    @Test
    void getAnalytics_mapsCategoryBreakdownCorrectly() {

        doReturn(List.of(
                category("Plumbing", 12),
                category("Electrical", 0))).when(analyticsRepository).countComplaintsByCategory(null, null);

        AnalyticsResponseDTO response = analyticsService.getAnalytics(null, null);

        assertThat(response.getCategoryBreakdown())
                .hasSize(2);

        assertThat(response.getCategoryBreakdown())
                .extracting("category")
                .containsExactly("Plumbing", "Electrical");

        assertThat(response.getCategoryBreakdown())
                .extracting("count")
                .containsExactly(12L, 0L);
    }

    @Test
    void getAnalytics_mapsFloorBreakdownCorrectly() {

        doReturn(List.of(
                floor("First", 10),
                floor("Second", 0))).when(analyticsRepository).countComplaintsByFloor(null, null);

        AnalyticsResponseDTO response = analyticsService.getAnalytics(null, null);

        assertThat(response.getFloorBreakdown())
                .hasSize(2);

        assertThat(response.getFloorBreakdown())
                .extracting("floor")
                .containsExactly("First", "Second");

        assertThat(response.getFloorBreakdown())
                .extracting("count")
                .containsExactly(10L, 0L);
    }

    @Test
    void getAnalytics_mapsRecurringRoomAndCommunityIssues() {

        doReturn(List.of(
                recurringIssue(
                        "ROOM", "First", "204",
                        null, "Plumbing", 5),
                recurringIssue(
                        "COMMUNITY_AREA", "Second", null,
                        "A", "Electrical", 3)))
                .when(analyticsRepository).findRecurringIssues(null, null);

        AnalyticsResponseDTO response = analyticsService.getAnalytics(null, null);

        assertThat(response.getRecurringIssues())
                .hasSize(2);

        var roomIssue = response.getRecurringIssues().get(0);

        assertThat(roomIssue.getType()).isEqualTo("ROOM");
        assertThat(roomIssue.getFloor()).isEqualTo("First");
        assertThat(roomIssue.getRoomNumber()).isEqualTo("204");
        assertThat(roomIssue.getCategory()).isEqualTo("Plumbing");
        assertThat(roomIssue.getCount()).isEqualTo(5);

        var communityIssue = response.getRecurringIssues().get(1);

        assertThat(communityIssue.getType()).isEqualTo("COMMUNITY_AREA");
        assertThat(communityIssue.getWing()).isEqualTo("A");
        assertThat(communityIssue.getCount()).isEqualTo(3);
    }

    // ---------------------------------------------------------
    // 3. Monthly trends
    // ---------------------------------------------------------

    @Test
    void getAnalytics_mergesCreatedAndResolvedMonthlyCounts() {

        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 12, 31);

        doReturn(List.of(
                monthlyCount("2026-10", 10),
                monthlyCount("2026-12", 5))).when(analyticsRepository)
                .countComplaintsCreatedByMonth(start, end);

        doReturn(List.of(
                monthlyCount("2026-10", 4),
                monthlyCount("2026-11", 7))).when(analyticsRepository)
                .countComplaintsResolvedByMonth(start, end);

        AnalyticsResponseDTO response = analyticsService.getAnalytics(start, end);

        List<MonthlyTrendDTO> trends = response.getMonthlyTrends();

        assertThat(trends).hasSize(3);

        assertThat(trends)
                .extracting("month")
                .containsExactly("2026-10", "2026-11", "2026-12");

        assertThat(trends)
                .extracting("created")
                .containsExactly(10L, 0L, 5L);

        assertThat(trends)
                .extracting("resolved")
                .containsExactly(4L, 7L, 0L);
    }

    @Test
    void getAnalytics_returnsExactlyLatest12MonthsWhenNoDateFilter() {

        doReturn(List.of(
                monthlyCount("2020-01", 100))).when(analyticsRepository)
                .countComplaintsCreatedByMonth(null, null);

        AnalyticsResponseDTO response = analyticsService.getAnalytics(null, null);

        List<MonthlyTrendDTO> trends = response.getMonthlyTrends();

        YearMonth currentMonth = YearMonth.now();
        YearMonth firstMonth = currentMonth.minusMonths(11);

        assertThat(trends).hasSize(12);

        assertThat(trends.get(0).getMonth())
                .isEqualTo(firstMonth.toString());

        assertThat(trends.get(11).getMonth())
                .isEqualTo(currentMonth.toString());

        assertThat(trends)
                .noneMatch(t -> t.getMonth().equals("2020-01"));
    }

    @Test
    void getAnalytics_generates12MonthsWhenOnlyEndDateIsProvided() {

        LocalDate end = LocalDate.of(2026, 10, 15);

        AnalyticsResponseDTO response = analyticsService.getAnalytics(null, end);

        List<MonthlyTrendDTO> trends = response.getMonthlyTrends();

        assertThat(trends).hasSize(12);

        assertThat(trends.get(0).getMonth()).isEqualTo("2025-11");
        assertThat(trends.get(11).getMonth()).isEqualTo("2026-10");
    }

    // ---------------------------------------------------------
    // 4. Date validation and repository interactions
    // ---------------------------------------------------------

    @Test
    void getAnalytics_throwsExceptionWhenStartDateIsAfterEndDate() {

        LocalDate start = LocalDate.of(2026, 11, 1);
        LocalDate end = LocalDate.of(2026, 10, 1);

        assertThatThrownBy(() -> analyticsService.getAnalytics(start, end))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Start date cannot be after end date");

        verifyNoInteractions(analyticsRepository);
    }

    @Test
    void getAnalytics_passesDateFiltersToAllRepositoryMethods() {

        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 31);

        analyticsService.getAnalytics(start, end);

        verify(analyticsRepository)
                .countComplaintsByStatus(start, end);

        verify(analyticsRepository)
                .findAverageResolutionTimeHours(start, end);

        verify(analyticsRepository)
                .countComplaintsByCategory(start, end);

        verify(analyticsRepository)
                .countComplaintsByFloor(start, end);

        verify(analyticsRepository)
                .findRecurringIssues(start, end);

        verify(analyticsRepository)
                .countComplaintsCreatedByMonth(start, end);

        verify(analyticsRepository)
                .countComplaintsResolvedByMonth(start, end);
    }

    // ---------------------------------------------------------
    // Helper methods for mocked repository projections
    // ---------------------------------------------------------

    private AnalyticsRepository.StatusCount status(
            String status,
            long count) {

        AnalyticsRepository.StatusCount result = org.mockito.Mockito.mock(
                AnalyticsRepository.StatusCount.class);

        org.mockito.Mockito.when(result.getStatus()).thenReturn(status);
        org.mockito.Mockito.when(result.getCount()).thenReturn(count);

        return result;
    }

    private AnalyticsRepository.CategoryCount category(
            String category,
            long count) {

        AnalyticsRepository.CategoryCount result = org.mockito.Mockito.mock(
                AnalyticsRepository.CategoryCount.class);

        org.mockito.Mockito.when(result.getCategory()).thenReturn(category);
        org.mockito.Mockito.when(result.getCount()).thenReturn(count);

        return result;
    }

    private AnalyticsRepository.FloorCount floor(
            String floor,
            long count) {

        AnalyticsRepository.FloorCount result = org.mockito.Mockito.mock(
                AnalyticsRepository.FloorCount.class);

        org.mockito.Mockito.when(result.getFloor()).thenReturn(floor);
        org.mockito.Mockito.when(result.getCount()).thenReturn(count);

        return result;
    }

    private AnalyticsRepository.RecurringIssue recurringIssue(
            String type,
            String floor,
            String roomNumber,
            String wing,
            String category,
            long count) {

        AnalyticsRepository.RecurringIssue result = org.mockito.Mockito.mock(
                AnalyticsRepository.RecurringIssue.class);

        org.mockito.Mockito.when(result.getType()).thenReturn(type);
        org.mockito.Mockito.when(result.getFloor()).thenReturn(floor);
        org.mockito.Mockito.when(result.getRoomNumber()).thenReturn(roomNumber);
        org.mockito.Mockito.when(result.getWing()).thenReturn(wing);
        org.mockito.Mockito.when(result.getCategory()).thenReturn(category);
        org.mockito.Mockito.when(result.getCount()).thenReturn(count);

        return result;
    }

    private AnalyticsRepository.MonthlyCount monthlyCount(
            String month,
            long count) {

        AnalyticsRepository.MonthlyCount result = org.mockito.Mockito.mock(
                AnalyticsRepository.MonthlyCount.class);

        org.mockito.Mockito.when(result.getMonth()).thenReturn(month);

        lenient().when(result.getCount()).thenReturn(count);

        return result;
    }
}

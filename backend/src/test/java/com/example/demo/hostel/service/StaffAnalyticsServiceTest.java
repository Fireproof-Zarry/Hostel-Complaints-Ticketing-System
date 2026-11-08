package com.example.demo.hostel.service;

import com.example.demo.hostel.dto.analytics.StaffAnalyticsDTO;
import com.example.demo.hostel.repository.AnalyticsRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** The Staff tab's figures, built from the work order rows AnalyticsRepository returns. */
@ExtendWith(MockitoExtension.class)
class StaffAnalyticsServiceTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Mock
    private AnalyticsRepository analyticsRepository;

    @InjectMocks
    private AnalyticsService analyticsService;

    @Test
    void staffWithNoWorkOrders_getAZeroRow() {
        when(analyticsRepository.findStaffMembers())
                .thenReturn(List.of(member("s1", "Ravi", "STAFF")));

        StaffAnalyticsDTO staff = analyticsService.getAnalytics(null, null).getStaff();

        assertThat(staff.members()).singleElement().satisfies(m -> {
            assertThat(m.name()).isEqualTo("Ravi");
            assertThat(m.assigned()).isZero();
            assertThat(m.active()).isZero();
            assertThat(m.completed()).isZero();
            assertThat(m.averageCompletionTimeHours()).isNull();
            assertThat(m.onTimeRate()).isNull();
        });
        assertThat(staff.averageCompletionTimeHours()).isNull();
        assertThat(staff.onTimeRate()).isNull();
    }

    @Test
    void countsActiveOverdueCompletedAndCancelledPerAssignee() {
        LocalDateTime nowIst = LocalDateTime.now(IST);

        when(analyticsRepository.findStaffMembers())
                .thenReturn(List.of(member("s1", "Ravi", "STAFF")));
        when(analyticsRepository.findStaffWorkOrders(any(), any())).thenReturn(List.of(
                // Active, past its deadline
                workOrder("s1", "Ravi", "STAFF", "OPEN",
                        server(nowIst.minusDays(3)), null, nowIst.minusDays(1), true, false),
                // Active, deadline ahead; created before the range
                workOrder("s1", "Ravi", "STAFF", "IN_PROGRESS",
                        server(nowIst.minusDays(3)), null, nowIst.plusDays(1), false, false),
                // Done in 10 hours, before its deadline
                workOrder("s1", "Ravi", "STAFF", "DONE",
                        server(nowIst.minusDays(2)), server(nowIst.minusDays(2).plusHours(10)),
                        nowIst.minusDays(2).plusHours(12), true, true),
                // Done in 20 hours, after its deadline
                workOrder("s1", "Ravi", "STAFF", "DONE",
                        server(nowIst.minusDays(2)), server(nowIst.minusDays(2).plusHours(20)),
                        nowIst.minusDays(2).plusHours(12), true, true),
                workOrder("s1", "Ravi", "STAFF", "CANCELLED",
                        server(nowIst.minusDays(2)), server(nowIst.minusDays(1)), null, true, true)));

        StaffAnalyticsDTO staff = analyticsService.getAnalytics(null, null).getStaff();

        assertThat(staff.members()).singleElement().satisfies(m -> {
            assertThat(m.assigned()).isEqualTo(4);
            assertThat(m.active()).isEqualTo(2);
            assertThat(m.overdue()).isEqualTo(1);
            assertThat(m.completed()).isEqualTo(2);
            assertThat(m.cancelled()).isEqualTo(1);
            assertThat(m.averageCompletionTimeHours()).isEqualTo(15.0);
            assertThat(m.onTimeRate()).isEqualTo(50.0);
        });
        assertThat(staff.activeWorkOrders()).isEqualTo(2);
        assertThat(staff.overdueWorkOrders()).isEqualTo(1);
        assertThat(staff.completedWorkOrders()).isEqualTo(2);
        assertThat(staff.averageCompletionTimeHours()).isEqualTo(15.0);
        assertThat(staff.onTimeRate()).isEqualTo(50.0);
    }

    @Test
    void finishedOutsideTheRange_isNotCounted() {
        LocalDateTime nowIst = LocalDateTime.now(IST);

        when(analyticsRepository.findStaffWorkOrders(any(), any())).thenReturn(List.of(
                workOrder("s1", "Ravi", "STAFF", "DONE",
                        server(nowIst.minusDays(40)), server(nowIst.minusDays(39)), null, true, false)));

        StaffAnalyticsDTO staff = analyticsService.getAnalytics(null, null).getStaff();

        assertThat(staff.members()).singleElement().satisfies(m -> {
            assertThat(m.assigned()).isEqualTo(1);
            assertThat(m.completed()).isZero();
        });
    }

    @Test
    void adminsAndFormerStaff_appearOnlyThroughTheirWorkOrders_sortedByName() {
        LocalDateTime nowIst = LocalDateTime.now(IST);

        when(analyticsRepository.findStaffMembers())
                .thenReturn(List.of(member("s1", "ravi", "STAFF")));
        when(analyticsRepository.findStaffWorkOrders(any(), any())).thenReturn(List.of(
                workOrder("a1", "Asha", "ADMIN", "OPEN",
                        server(nowIst.minusHours(1)), null, null, true, false),
                workOrder("f1", "Zoya", "STUDENT", "DONE",
                        server(nowIst.minusDays(1)), server(nowIst.minusHours(1)), null, true, true)));

        StaffAnalyticsDTO staff = analyticsService.getAnalytics(null, null).getStaff();

        assertThat(staff.members())
                .extracting(StaffAnalyticsDTO.Member::name, StaffAnalyticsDTO.Member::role)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Asha", "ADMIN"),
                        org.assertj.core.groups.Tuple.tuple("ravi", "STAFF"),
                        org.assertj.core.groups.Tuple.tuple("Zoya", "STUDENT"));
    }

    // Stored timestamps are in the server's zone; deadlines are IST
    private static LocalDateTime server(LocalDateTime ist) {
        return ist.atZone(IST).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }

    private static AnalyticsRepository.StaffMember member(String id, String name, String role) {
        return new Row(id, name, role, null, null, null, null, null, null);
    }

    private static AnalyticsRepository.StaffWorkOrder workOrder(
            String id, String name, String role, String status,
            LocalDateTime createdAt, LocalDateTime finishedAt, LocalDateTime deadline,
            boolean createdInRange, boolean finishedInRange) {
        return new Row(id, name, role, status, createdAt, finishedAt, deadline,
                createdInRange, finishedInRange);
    }

    private record Row(
            String userId, String name, String role, String status,
            LocalDateTime createdAt, LocalDateTime finishedAt, LocalDateTime deadline,
            Boolean createdInRange, Boolean finishedInRange)
            implements AnalyticsRepository.StaffWorkOrder {

        @Override public String getUserId() { return userId; }
        @Override public String getEmail() { return userId + "@example.com"; }
        @Override public String getName() { return name; }
        @Override public String getRole() { return role; }
        @Override public String getStatus() { return status; }
        @Override public LocalDateTime getCreatedAt() { return createdAt; }
        @Override public LocalDateTime getFinishedAt() { return finishedAt; }
        @Override public LocalDateTime getDeadline() { return deadline; }
        @Override public Boolean getCreatedInRange() { return createdInRange; }
        @Override public Boolean getFinishedInRange() { return finishedInRange; }
    }
}

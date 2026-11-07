package com.example.demo.hostel.service;

import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.model.Status;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** The 7-day rule, at exact moments, with no database or clock involved. */
class NudgeServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 7, 12, 0);

    private static Complaint complaint(Status status, LocalDateTime createdAt, LocalDateTime lastNudgedAt) {
        Complaint complaint = new Complaint();
        complaint.setStatus(status);
        complaint.setCreatedAt(createdAt);
        complaint.setLastNudgedAt(lastNudgedAt);
        return complaint;
    }

    @Test
    void exactlySevenDaysOldIsStillTooEarly() {
        assertThat(NudgeService.canNudge(complaint(Status.PENDING, NOW.minusDays(7), null), NOW)).isFalse();
    }

    @Test
    void oneNanosecondPastSevenDaysIsAllowed() {
        Complaint complaint = complaint(Status.PENDING, NOW.minusDays(7).minusNanos(1), null);

        assertThat(NudgeService.canNudge(complaint, NOW)).isTrue();
    }

    @Test
    void everyUnresolvedStatusCanBeNudged() {
        for (Status status : new Status[] {Status.PENDING, Status.ASSIGNED, Status.IN_PROGRESS}) {
            assertThat(NudgeService.canNudge(complaint(status, NOW.minusDays(8), null), NOW)).isTrue();
        }
    }

    @Test
    void closedComplaintsCanNeverBeNudged() {
        for (Status status : new Status[] {Status.RESOLVED, Status.REJECTED}) {
            Complaint complaint = complaint(status, NOW.minusDays(30), null);

            assertThat(NudgeService.canNudge(complaint, NOW)).isFalse();
            assertThat(NudgeService.nextNudgeAt(complaint)).isNull();
        }
    }

    @Test
    void waitsSevenDaysAfterTheLastNudge() {
        LocalDateTime lastNudged = NOW.minusDays(3);
        Complaint complaint = complaint(Status.PENDING, NOW.minusDays(20), lastNudged);

        assertThat(NudgeService.canNudge(complaint, NOW)).isFalse();
        assertThat(NudgeService.nextNudgeAt(complaint)).isEqualTo(lastNudged.plusDays(7));
        assertThat(NudgeService.canNudge(complaint, lastNudged.plusDays(7).plusNanos(1))).isTrue();
    }

    @Test
    void complaintWithoutCreationTimeIsNotNudgeable() {
        assertThat(NudgeService.canNudge(complaint(Status.PENDING, null, null), NOW)).isFalse();
    }
}

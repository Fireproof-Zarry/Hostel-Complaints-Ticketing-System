package com.example.demo.hostel.service;

import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.model.Status;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/** The opening rule at exact moments, with no database or clock involved. */
class NudgeServiceTest {

    private static final ZoneId UTC = ZoneOffset.UTC;
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    // 13 Nov, 5 AM IST, stored as UTC (the server's zone in production)
    private static final LocalDateTime OPENS_13TH = LocalDateTime.of(2026, 11, 12, 23, 30);

    private static LocalDateTime ist(int day, int hour, int minute) {
        return LocalDateTime.of(2026, 11, day, hour, minute).atZone(IST)
                .withZoneSameInstant(UTC).toLocalDateTime();
    }

    private static Complaint complaint(Status status, LocalDateTime createdAt, LocalDateTime lastNudgedAt) {
        Complaint complaint = new Complaint();
        complaint.setStatus(status);
        complaint.setCreatedAt(createdAt);
        complaint.setLastNudgedAt(lastNudgedAt);
        return complaint;
    }

    @Test
    void filedAnyTimeOnThe6thOpensAt5amOnThe13th() {
        for (LocalDateTime filed : new LocalDateTime[] {ist(6, 0, 0), ist(6, 4, 59), ist(6, 18, 15), ist(6, 23, 59)}) {
            assertThat(NudgeService.nextNudgeAt(complaint(Status.PENDING, filed, null), UTC))
                    .as("filed %s UTC", filed)
                    .isEqualTo(OPENS_13TH);
        }
    }

    @Test
    void opensExactlyAt5amNotAMomentBefore() {
        Complaint complaint = complaint(Status.PENDING, ist(6, 18, 15), null);

        assertThat(NudgeService.canNudge(complaint, OPENS_13TH.minusNanos(1), UTC)).isFalse();
        assertThat(NudgeService.canNudge(complaint, OPENS_13TH, UTC)).isTrue();
    }

    @Test
    void usesTheIstDayEvenWhenTheServerRunsInUtc() {
        // 6 Nov 11:30 PM UTC is already the 7th in IST, so it opens on the 14th
        Complaint complaint = complaint(Status.PENDING, ist(7, 5, 0), null);

        assertThat(NudgeService.nextNudgeAt(complaint, UTC)).isEqualTo(OPENS_13TH.plusDays(1));
    }

    @Test
    void worksWhenTheServerRunsInIst() {
        Complaint complaint = complaint(Status.PENDING, LocalDateTime.of(2026, 11, 6, 18, 15), null);

        assertThat(NudgeService.nextNudgeAt(complaint, IST)).isEqualTo(LocalDateTime.of(2026, 11, 13, 5, 0));
    }

    @Test
    void everyUnresolvedStatusCanBeNudged() {
        for (Status status : new Status[] {Status.PENDING, Status.ASSIGNED, Status.IN_PROGRESS}) {
            assertThat(NudgeService.canNudge(complaint(status, ist(6, 12, 0), null), OPENS_13TH, UTC)).isTrue();
        }
    }

    @Test
    void closedComplaintsCanNeverBeNudged() {
        for (Status status : new Status[] {Status.RESOLVED, Status.REJECTED}) {
            Complaint complaint = complaint(status, ist(1, 12, 0), null);

            assertThat(NudgeService.canNudge(complaint, OPENS_13TH, UTC)).isFalse();
            assertThat(NudgeService.nextNudgeAt(complaint, UTC)).isNull();
        }
    }

    @Test
    void nextNudgeOpensAt5amSevenDaysAfterTheLastNudgesDay() {
        Complaint complaint = complaint(Status.PENDING, ist(1, 12, 0), ist(6, 21, 0));

        assertThat(NudgeService.nextNudgeAt(complaint, UTC)).isEqualTo(OPENS_13TH);
        assertThat(NudgeService.canNudge(complaint, OPENS_13TH.minusMinutes(1), UTC)).isFalse();
    }

    @Test
    void complaintWithoutCreationTimeIsNotNudgeable() {
        assertThat(NudgeService.canNudge(complaint(Status.PENDING, null, null), OPENS_13TH, UTC)).isFalse();
    }

    // The database UPDATE uses the cutoff; the API and the page use nextNudgeAt.
    // They must never disagree.
    @Test
    void cutoffAgreesWithNextNudgeAtAtEveryMoment() {
        Random random = new Random(42);
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0);
        for (ZoneId storageZone : new ZoneId[] {UTC, IST, ZoneId.of("America/New_York")}) {
            for (int i = 0; i < 20_000; i++) {
                LocalDateTime filed = start.plusMinutes(random.nextInt(60 * 24 * 60));
                LocalDateTime now = filed.plusMinutes(random.nextInt(60 * 24 * 10));
                LocalDateTime cutoff = NudgeService.cutoff(now, storageZone);

                boolean byCutoff = filed.isBefore(cutoff);
                boolean byOpening = !now.isBefore(NudgeService.opensAt(filed, storageZone));
                assertThat(byCutoff).as("%s filed %s, now %s", storageZone, filed, now).isEqualTo(byOpening);
            }
        }
    }
}

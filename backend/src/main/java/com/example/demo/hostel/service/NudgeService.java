package com.example.demo.hostel.service;

import com.example.demo.hostel.dto.NudgeResponse;
import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.model.Role;
import com.example.demo.hostel.model.Status;
import com.example.demo.hostel.model.User;
import com.example.demo.hostel.repository.ComplaintRepository;
import com.example.demo.hostel.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

/**
 * Lets the owner of an unresolved complaint push the admins. The button opens at
 * 5 AM hostel time (IST) on the 7th day after the complaint was filed, whatever
 * time it was filed: filed any time on the 6th, it opens at 5 AM on the 13th.
 * After a nudge, the next one opens the same way, counted from the nudge's day.
 *
 * Times are stored as LocalDateTime in the server's time zone (what
 * {@code @CreationTimestamp} and {@code LocalDateTime.now()} use), so they are
 * converted to IST to find the day.
 */
@Service
public class NudgeService {

    static final int NUDGE_WAIT_DAYS = 7;
    static final ZoneId HOSTEL_ZONE = ZoneId.of("Asia/Kolkata");
    static final LocalTime OPENS_AT = LocalTime.of(5, 0);

    private static final Set<Status> UNRESOLVED = Set.of(Status.PENDING, Status.ASSIGNED, Status.IN_PROGRESS);

    private final ComplaintRepository complaintRepository;
    private final UserRepository userRepository;
    private final FcmNotificationService fcmNotificationService;

    public NudgeService(
            ComplaintRepository complaintRepository,
            UserRepository userRepository,
            FcmNotificationService fcmNotificationService) {
        this.complaintRepository = complaintRepository;
        this.userRepository = userRepository;
        this.fcmNotificationService = fcmNotificationService;
    }

    public static boolean isUnresolved(Complaint complaint) {
        return UNRESOLVED.contains(complaint.getStatus());
    }

    /** 5 AM IST on the 7th day after the IST day of {@code time}, in the storage zone. */
    static LocalDateTime opensAt(LocalDateTime time, ZoneId storageZone) {
        LocalDate day = time.atZone(storageZone).withZoneSameInstant(HOSTEL_ZONE).toLocalDate();
        return day.plusDays(NUDGE_WAIT_DAYS).atTime(OPENS_AT).atZone(HOSTEL_ZONE)
                .withZoneSameInstant(storageZone).toLocalDateTime();
    }

    /**
     * At {@code now}, anything earlier than this is long enough ago:
     * {@code opensAt(time) <= now} exactly when {@code time < cutoff(now)}. The
     * latest 5 AM IST that has passed opens day E - 7 and every day before it,
     * so the cutoff is the start of day E - 6.
     */
    static LocalDateTime cutoff(LocalDateTime now, ZoneId storageZone) {
        LocalDate latestOpening = now.atZone(storageZone).withZoneSameInstant(HOSTEL_ZONE)
                .minusHours(OPENS_AT.getHour()).minusMinutes(OPENS_AT.getMinute())
                .toLocalDate();
        return latestOpening.minusDays(NUDGE_WAIT_DAYS - 1L).atStartOfDay(HOSTEL_ZONE)
                .withZoneSameInstant(storageZone).toLocalDateTime();
    }

    /** When the button opens, or null if this complaint cannot be nudged at all. */
    public static LocalDateTime nextNudgeAt(Complaint complaint) {
        return nextNudgeAt(complaint, ZoneId.systemDefault());
    }

    static LocalDateTime nextNudgeAt(Complaint complaint, ZoneId storageZone) {
        if (!isUnresolved(complaint) || complaint.getCreatedAt() == null) {
            return null;
        }
        LocalDateTime oldEnough = opensAt(complaint.getCreatedAt(), storageZone);
        LocalDateTime lastNudged = complaint.getLastNudgedAt();
        if (lastNudged == null) {
            return oldEnough;
        }
        LocalDateTime cooledDown = opensAt(lastNudged, storageZone);
        return cooledDown.isAfter(oldEnough) ? cooledDown : oldEnough;
    }

    public static boolean canNudge(Complaint complaint, LocalDateTime now) {
        return canNudge(complaint, now, ZoneId.systemDefault());
    }

    static boolean canNudge(Complaint complaint, LocalDateTime now, ZoneId storageZone) {
        LocalDateTime next = nextNudgeAt(complaint, storageZone);
        return next != null && !now.isBefore(next);
    }

    /**
     * Records the nudge, then notifies the admins once it is committed. The
     * conditional UPDATE repeats the checks, so of two simultaneous nudges only
     * one succeeds and only one notification goes out.
     */
    @Transactional
    public NudgeResponse nudge(Long complaintId, String email) {
        User requester = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        // Someone else's complaint gets the same 404, so its existence is not revealed
        Complaint complaint = complaintRepository.findByIdAndStudent(complaintId, requester)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Complaint not found"));

        LocalDateTime now = LocalDateTime.now();
        requireNudgeable(complaint, now);

        String title = complaint.getTitle();
        ZoneId storageZone = ZoneId.systemDefault();
        int updated = complaintRepository.recordNudge(complaintId, UNRESOLVED, now, cutoff(now, storageZone));
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This complaint was just nudged.");
        }

        List<String> adminTokens = userRepository.findFcmTokensByRole(Role.ADMIN);
        ComplaintTransitionService.runAfterCommit(
                () -> fcmNotificationService.sendNudgeNotification(adminTokens, complaintId, title));

        return new NudgeResponse(complaintId, now, opensAt(now, storageZone));
    }

    private static void requireNudgeable(Complaint complaint, LocalDateTime now) {
        if (!isUnresolved(complaint)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only unresolved complaints can be nudged.");
        }
        if (!canNudge(complaint, now)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, complaint.getLastNudgedAt() == null
                    ? "A complaint can be nudged from 5 AM on the " + NUDGE_WAIT_DAYS + "th day after it was filed."
                    : "This complaint was nudged recently. Try again from 5 AM, " + NUDGE_WAIT_DAYS
                            + " days after that nudge.");
        }
    }
}

package com.example.demo.hostel.service;

import com.example.demo.hostel.model.Status;
import com.example.demo.hostel.repository.ComplaintRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Soft-archives RESOLVED and REJECTED complaints that have not changed for
 * 30 days, keeping the admin dashboard query small.
 */
@Service
public class ComplaintArchivalService {

    static final int ARCHIVE_AFTER_DAYS = 30;
    private static final List<Status> FINAL_STATUSES = List.of(Status.RESOLVED, Status.REJECTED);

    private static final Logger log = LoggerFactory.getLogger(ComplaintArchivalService.class);

    private final ComplaintRepository complaintRepository;

    public ComplaintArchivalService(ComplaintRepository complaintRepository) {
        this.complaintRepository = complaintRepository;
    }

    /** Runs daily at 2:00 AM India time (the server itself may run in UTC). */
    @Scheduled(cron = "0 0 2 * * ?", zone = "Asia/Kolkata")
    @Transactional
    public int archiveOldComplaints() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(ARCHIVE_AFTER_DAYS);
        int archived = complaintRepository.archiveCompletedBefore(FINAL_STATUSES, cutoff);
        log.info("Archived {} resolved/rejected complaints last updated before {}", archived, cutoff);
        return archived;
    }
}

package com.example.demo.hostel.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Hard-deletes complaints 15 days after their owner deleted them (soft delete
 * sets complaints.deleted_at). Uses plain SQL because entity queries cannot
 * see soft-deleted rows.
 */
@Service
public class ComplaintPurgeService {

    static final int RETENTION_DAYS = 15;

    private static final Logger log = LoggerFactory.getLogger(ComplaintPurgeService.class);

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final SupabaseStorageService storageService;

    public ComplaintPurgeService(
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactionTemplate,
            SupabaseStorageService storageService) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.storageService = storageService;
    }

    /** Runs daily at 3:00 AM India time, an hour after the archival job. */
    @Scheduled(cron = "0 0 3 * * ?", zone = "Asia/Kolkata")
    public int purgeDeletedComplaints() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(RETENTION_DAYS);
        List<Map<String, Object>> due = jdbcTemplate.queryForList(
                "SELECT id, image_url FROM complaints WHERE deleted_at IS NOT NULL AND deleted_at < ?",
                Timestamp.valueOf(cutoff));

        int purged = 0;
        int skipped = 0;
        for (Map<String, Object> row : due) {
            long id = ((Number) row.get("id")).longValue();
            String imageUrl = (String) row.get("image_url");

            // Photo first: if storage fails, keep the row so the next run retries,
            // rather than losing track of a still-public image
            if (!storageService.deleteImage(imageUrl)) {
                log.warn("Skipping purge of complaint {}: its image could not be deleted from storage", id);
                skipped++;
                continue;
            }

            // complaint_history has no ON DELETE CASCADE; upvotes, work_orders and
            // comments are removed by their foreign keys' ON DELETE CASCADE
            transactionTemplate.executeWithoutResult(tx -> {
                jdbcTemplate.update("DELETE FROM complaint_history WHERE complaint_id = ?", id);
                jdbcTemplate.update("DELETE FROM complaints WHERE id = ?", id);
            });
            purged++;
        }

        log.info("Purged {} deleted complaints older than {} days ({} skipped)", purged, RETENTION_DAYS, skipped);
        return purged;
    }
}

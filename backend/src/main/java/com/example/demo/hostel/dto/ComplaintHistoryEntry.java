package com.example.demo.hostel.dto;

import com.example.demo.hostel.model.Status;

import java.time.LocalDateTime;

/**
 * One status change shown to the complaint's owner. Deliberately excludes the
 * actor's account details (email, phone): admins appear as "Hostel office".
 */
public record ComplaintHistoryEntry(
        Status oldStatus,
        Status newStatus,
        String reason,
        LocalDateTime changedAt,
        String changedBy
) {
}

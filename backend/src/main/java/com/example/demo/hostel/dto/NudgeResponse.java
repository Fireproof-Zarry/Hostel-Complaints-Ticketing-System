package com.example.demo.hostel.dto;

import java.time.LocalDateTime;

/** Result of a nudge: when it happened and when the next one is allowed. */
public record NudgeResponse(Long complaintId, LocalDateTime lastNudgedAt, LocalDateTime nextNudgeAt) {
}

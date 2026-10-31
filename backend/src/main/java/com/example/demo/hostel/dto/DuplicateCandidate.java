package com.example.demo.hostel.dto;

import com.example.demo.hostel.model.Status;

import java.time.LocalDateTime;

/**
 * A possibly duplicate community complaint shown while a student types a new
 * one. Deliberately excludes the reporting student's details.
 */
public record DuplicateCandidate(
        Long id,
        String title,
        Status status,
        String floor,
        String wing,
        long upvoteCount,
        LocalDateTime createdAt,
        boolean reportedByYou,
        boolean upvotedByYou
) {
}

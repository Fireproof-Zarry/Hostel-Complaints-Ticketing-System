package com.example.demo.hostel.dto;

import com.example.demo.hostel.model.Status;

import java.time.LocalDateTime;

/**
 * A possibly duplicate open complaint (community area, or the student's own
 * room) shown while a student types a new one. Deliberately excludes the
 * reporting student's details.
 */
public record DuplicateCandidate(
        Long id,
        String title,
        Status status,
        String floor,
        String wing,
        String room,
        long upvoteCount,
        LocalDateTime createdAt,
        boolean reportedByYou,
        boolean upvotedByYou
) {
}

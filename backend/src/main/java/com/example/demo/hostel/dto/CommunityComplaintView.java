package com.example.demo.hostel.dto;

import com.example.demo.hostel.model.Status;

import java.time.LocalDateTime;

/**
 * A community-area complaint on the community dashboard. Built directly by
 * the repository query; deliberately excludes the reporting student's details.
 */
public record CommunityComplaintView(
        Long id,
        String title,
        String description,
        Status status,
        String category,
        String floor,
        String wing,
        String imageUrl,
        LocalDateTime createdAt,
        Long upvoteCount,
        Boolean reportedByYou,
        Boolean upvotedByYou
) {
}

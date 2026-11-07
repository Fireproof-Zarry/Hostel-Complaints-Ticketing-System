package com.example.demo.hostel.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

/**
 * Body of POST /api/complaints. For a personal issue (isCommunityArea false)
 * the room comes from the student's profile, so floor/room/wing are ignored.
 * For a community area, floor and wing are required.
 */
public record CreateComplaintRequest(
        String title,
        String description,
        String category,
        @JsonAlias("is_community_area") Boolean isCommunityArea,
        String floor,
        String room,
        String wing,
        @JsonAlias("available_time_slot") String availableTimeSlot,
        @JsonAlias("key_with_security") Boolean keyWithSecurity
) {
    /** A missing value means a room complaint. */
    public boolean communityArea() {
        return Boolean.TRUE.equals(isCommunityArea);
    }

    /** A missing value means the key was not left with security. */
    public boolean keyLeftWithSecurity() {
        return Boolean.TRUE.equals(keyWithSecurity);
    }
}

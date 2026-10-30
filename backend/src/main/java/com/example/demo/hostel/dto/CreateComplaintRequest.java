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
    public boolean communityArea() {
        return Boolean.TRUE.equals(isCommunityArea);
    }

    public boolean keyLeftWithSecurity() {
        return Boolean.TRUE.equals(keyWithSecurity);
    }
}

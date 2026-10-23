package com.example.demo.hostel.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

/** Body of PUT /api/users/{id}/profile. Blank values clear the field. */
public record ProfileUpdateRequest(
        @JsonAlias("full_name") String fullName,
        @JsonAlias("phone_number") String phoneNumber,
        @JsonAlias("roll_number") String rollNumber,
        @JsonAlias("room_number") String roomNumber
) {
}

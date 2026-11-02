package com.example.demo.hostel.dto;

/** A hostel staff member as listed in staff management. */
public record StaffMemberView(
        String id,
        String email,
        String name,
        String phoneNumber,
        long activeWorkOrders
) {
}

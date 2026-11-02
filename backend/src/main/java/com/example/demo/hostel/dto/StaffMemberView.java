package com.example.demo.hostel.dto;

import com.example.demo.hostel.model.Role;

/**
 * A staff member or admin as listed in staff management. invitePending: added
 * by email but has not signed in yet (their name is still the email).
 */
public record StaffMemberView(
        String id,
        String email,
        String name,
        String phoneNumber,
        Role role,
        long activeWorkOrders,
        long overdueWorkOrders,
        boolean invitePending
) {
}

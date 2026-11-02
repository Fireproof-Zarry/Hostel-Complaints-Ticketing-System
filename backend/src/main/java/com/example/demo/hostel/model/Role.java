package com.example.demo.hostel.model;

/**
 * STUDENT: @smail account. ADMIN: email in ADMIN_EMAILS. STAFF: hostel staff
 * (any verified email) added by an admin; same complaint view as admins
 * except staff management, and can assign complaints to themselves.
 */
public enum Role {
    STUDENT, ADMIN, STAFF
}

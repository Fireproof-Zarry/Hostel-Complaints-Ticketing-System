package com.example.demo.hostel.model;

/** Where a complaint is in the workflow. ComplaintTransitionService defines the allowed moves. */
public enum Status {
    PENDING, ASSIGNED, IN_PROGRESS, RESOLVED, REJECTED
}
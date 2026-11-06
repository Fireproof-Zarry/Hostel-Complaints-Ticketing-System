package com.example.demo.hostel.model;

/** OPEN -> IN_PROGRESS -> DONE; OPEN or IN_PROGRESS can be CANCELLED. */
public enum WorkOrderStatus {
    OPEN, IN_PROGRESS, DONE, CANCELLED;

    public boolean isActive() {
        return this == OPEN || this == IN_PROGRESS;
    }
}

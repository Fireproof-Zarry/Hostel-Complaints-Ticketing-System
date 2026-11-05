package com.example.demo.hostel.dto;

import com.example.demo.hostel.model.WorkOrderPriority;

import java.time.LocalDateTime;

/** inChargeId null means "assign me"; staff can only assign themselves. */
public record AssignWorkOrderRequest(
        String inChargeId,
        WorkOrderPriority priority,
        LocalDateTime deadline
) {
}

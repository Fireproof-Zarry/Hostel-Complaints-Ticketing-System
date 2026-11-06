package com.example.demo.hostel.dto;

import com.example.demo.hostel.model.WorkOrderPriority;
import com.example.demo.hostel.model.WorkOrderStatus;

import java.time.LocalDateTime;

/** Only non-null fields are changed. */
public record UpdateWorkOrderRequest(
        WorkOrderPriority priority,
        LocalDateTime deadline,
        String completionNotes,
        WorkOrderStatus status
) {
}

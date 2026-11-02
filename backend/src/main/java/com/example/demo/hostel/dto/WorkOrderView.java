package com.example.demo.hostel.dto;

import com.example.demo.hostel.model.Status;
import com.example.demo.hostel.model.WorkOrderPriority;
import com.example.demo.hostel.model.WorkOrderStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/** A work order as returned by the API (never the entity). */
public record WorkOrderView(
        UUID id,
        Long complaintId,
        String complaintTitle,
        Status complaintStatus,
        String assigneeId,
        String assigneeName,
        String assigneeEmail,
        WorkOrderPriority priority,
        WorkOrderStatus status,
        LocalDateTime deadline,
        String completionNotes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

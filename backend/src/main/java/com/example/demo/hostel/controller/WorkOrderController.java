
package com.example.demo.hostel.controller;

import com.example.demo.hostel.model.WorkOrder;
import com.example.demo.hostel.service.WorkOrderService;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/work-orders")
public class WorkOrderController {

    private final WorkOrderService workOrderService;

    public WorkOrderController(WorkOrderService workOrderService) {
        this.workOrderService = workOrderService;
    }

    // Create and assign a work order
    @PostMapping
    public ResponseEntity<WorkOrder> createWorkOrder(
            @RequestParam Long complaintId,
            @RequestParam String assigneeId,
            @RequestParam String priority,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime deadline) {

        WorkOrder workOrder = workOrderService.assignWorkOrder(
                complaintId, assigneeId, priority, deadline);

        return ResponseEntity.status(HttpStatus.CREATED).body(workOrder);
    }

    // Get all work orders
    @GetMapping
    public List<WorkOrder> getAllWorkOrders() {
        return workOrderService.getAllWorkOrders();
    }

    // Get a work order by ID
    @GetMapping("/{id}")
    public WorkOrder getWorkOrder(@PathVariable UUID id) {
        return workOrderService.getWorkOrder(id);
    }

    // Get work orders assigned to a staff member
    @GetMapping("/assignee/{assigneeId}")
    public List<WorkOrder> getWorkOrdersByAssignee(
            @PathVariable String assigneeId) {
        return workOrderService.getWorkOrdersByAssignee(assigneeId);
    }

    @PatchMapping("/{id}/status")
    public WorkOrder updateWorkOrderStatus(
            @PathVariable UUID id,
            @RequestParam String status,
            @RequestParam(required = false) String completionNotes) {

        return workOrderService.updateWorkOrderStatus(
                id, status, completionNotes);
    }
}

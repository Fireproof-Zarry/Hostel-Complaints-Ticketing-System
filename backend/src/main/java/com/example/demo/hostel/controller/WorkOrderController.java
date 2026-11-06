package com.example.demo.hostel.controller;

import com.example.demo.hostel.dto.AssignWorkOrderRequest;
import com.example.demo.hostel.dto.UpdateWorkOrderRequest;
import com.example.demo.hostel.dto.WorkOrderView;
import com.example.demo.hostel.service.WorkOrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Work orders for hostel staff and admins (SecurityConfig: /api/work-orders/**). */
@RestController
@RequestMapping("/api/work-orders")
public class WorkOrderController {

    private final WorkOrderService workOrderService;

    public WorkOrderController(WorkOrderService workOrderService) {
        this.workOrderService = workOrderService;
    }

    /** Assign a complaint: omit inChargeId to assign yourself (staff can only assign themselves). */
    @PostMapping("/{complaintId}/assign")
    public ResponseEntity<WorkOrderView> assign(
            @PathVariable Long complaintId,
            @RequestBody AssignWorkOrderRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(workOrderService.assignInCharge(
                complaintId, request.inChargeId(), request.priority(), request.deadline(), email(jwt)));
    }

    /** Update priority, deadline, completion notes or status (assignee or admin). */
    @PutMapping("/{id}")
    public ResponseEntity<WorkOrderView> update(
            @PathVariable UUID id,
            @RequestBody UpdateWorkOrderRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(workOrderService.updateWorkOrder(id, request, email(jwt)));
    }

    /** The caller's active oversight queue. */
    @GetMapping("/me")
    public List<WorkOrderView> myQueue(@AuthenticationPrincipal Jwt jwt) {
        return workOrderService.getMyQueue(email(jwt));
    }

    /** A complaint's work orders, newest first. */
    @GetMapping("/complaint/{complaintId}")
    public List<WorkOrderView> forComplaint(@PathVariable Long complaintId, @AuthenticationPrincipal Jwt jwt) {
        return workOrderService.getForComplaint(complaintId, email(jwt));
    }

    private static String email(Jwt jwt) {
        return jwt.getClaimAsString("email");
    }
}

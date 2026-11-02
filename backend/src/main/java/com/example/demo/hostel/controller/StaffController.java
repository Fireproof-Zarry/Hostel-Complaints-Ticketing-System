package com.example.demo.hostel.controller;

import com.example.demo.hostel.dto.StaffMemberView;
import com.example.demo.hostel.dto.WorkOrderView;
import com.example.demo.hostel.service.StaffService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Staff management. Admin only (SecurityConfig: /api/admin/staff/**). */
@RestController
@RequestMapping("/api/admin/staff")
public class StaffController {

    private final StaffService staffService;

    public StaffController(StaffService staffService) {
        this.staffService = staffService;
    }

    @GetMapping
    public List<StaffMemberView> listStaff() {
        return staffService.listStaff();
    }

    @GetMapping("/{userId}/work-orders")
    public List<WorkOrderView> getActiveWorkOrders(@PathVariable String userId) {
        return staffService.getActiveWorkOrders(userId);
    }

    /** Body: {"email": "..."} */
    @PostMapping
    public ResponseEntity<StaffMemberView> addStaff(@RequestBody Map<String, String> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(staffService.addStaff(body.get("email")));
    }

    /** reassignTo: user id to move their active work orders to; omitted cancels them. */
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> removeStaff(
            @PathVariable String userId,
            @RequestParam(required = false) String reassignTo,
            @AuthenticationPrincipal Jwt jwt) {
        staffService.removeStaff(userId, reassignTo, jwt.getClaimAsString("email"));
        return ResponseEntity.noContent().build();
    }
}

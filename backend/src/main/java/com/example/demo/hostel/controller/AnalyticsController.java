
package com.example.demo.hostel.controller;

import com.example.demo.hostel.dto.analytics.AnalyticsResponseDTO;
import com.example.demo.hostel.service.AnalyticsService;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/** The analytics dashboard's data, for admins and staff (SecurityConfig: /api/admin/**). */
@RestController
@RequestMapping("/api/admin/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /**
     * All dashboard figures. startDate and endDate are optional and inclusive; a start after
     * the end is a 400.
     */
    @GetMapping
    public ResponseEntity<?> getAnalytics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,

            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        try {
            AnalyticsResponseDTO response = analyticsService.getAnalytics(startDate, endDate);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(e.getMessage());
        }
    }
}

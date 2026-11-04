
package com.example.demo.hostel.controller;

import com.example.demo.hostel.dto.analytics.AnalyticsResponseDTO;
import com.example.demo.hostel.service.AnalyticsService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpStatus;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnalyticsControllerTest {

    private AnalyticsService analyticsService;
    private AnalyticsController controller;

    private AnalyticsResponseDTO analyticsResponse;

    @BeforeEach
    void setUp() {
        analyticsService = mock(AnalyticsService.class);
        controller = new AnalyticsController(analyticsService);
        analyticsResponse = new AnalyticsResponseDTO();
    }

    // ---------------------------------------------------------
    // 1. Successful requests
    // ---------------------------------------------------------

    @Test
    void getsAnalyticsWithoutDateFilters() {

        when(analyticsService.getAnalytics(null, null))
                .thenReturn(analyticsResponse);

        var response = controller.getAnalytics(null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(analyticsResponse, response.getBody());

        verify(analyticsService).getAnalytics(null, null);
    }

    @Test
    void getsAnalyticsWithDateFilters() {

        LocalDate startDate = LocalDate.of(2026, 10, 1);
        LocalDate endDate = LocalDate.of(2026, 10, 31);

        when(analyticsService.getAnalytics(startDate, endDate))
                .thenReturn(analyticsResponse);

        var response = controller.getAnalytics(startDate, endDate);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(analyticsResponse, response.getBody());

        verify(analyticsService).getAnalytics(startDate, endDate);
    }

    @Test
    void getsAnalyticsWithOnlyStartDate() {

        LocalDate startDate = LocalDate.of(2026, 10, 1);

        when(analyticsService.getAnalytics(startDate, null))
                .thenReturn(analyticsResponse);

        var response = controller.getAnalytics(startDate, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(analyticsResponse, response.getBody());

        verify(analyticsService).getAnalytics(startDate, null);
    }

    @Test
    void getsAnalyticsWithOnlyEndDate() {

        LocalDate endDate = LocalDate.of(2026, 10, 31);

        when(analyticsService.getAnalytics(null, endDate))
                .thenReturn(analyticsResponse);

        var response = controller.getAnalytics(null, endDate);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(analyticsResponse, response.getBody());

        verify(analyticsService).getAnalytics(null, endDate);
    }

    // ---------------------------------------------------------
    // 2. Invalid date range
    // ---------------------------------------------------------

    @Test
    void returnsBadRequestWhenStartDateIsAfterEndDate() {

        LocalDate startDate = LocalDate.of(2026, 11, 1);
        LocalDate endDate = LocalDate.of(2026, 10, 1);

        when(analyticsService.getAnalytics(startDate, endDate))
                .thenThrow(new IllegalArgumentException(
                        "Start date cannot be after end date"));

        var response = controller.getAnalytics(startDate, endDate);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Start date cannot be after end date",
                response.getBody());

        verify(analyticsService).getAnalytics(startDate, endDate);
    }

}
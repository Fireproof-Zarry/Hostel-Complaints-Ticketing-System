package com.example.demo.hostel.controller;

import com.example.demo.hostel.model.Status;
import com.example.demo.hostel.service.AdminService;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class AdminControllerTest {

    private MockMvc mockMvc(AdminService adminService) {
        return standaloneSetup(new AdminController(adminService)).build();
    }

    @Test
    void getComplaints_withoutFilters_returns200() throws Exception {
        AdminService adminService = mock(AdminService.class);

        when(adminService.getComplaints(null, null, null, null))
                .thenReturn(List.of());

        mockMvc(adminService)
                .perform(get("/api/admin/complaints"))
                .andExpect(status().isOk());

        verify(adminService)
                .getComplaints(null, null, null, null);
    }

    @Test
    void getComplaints_withStatusFilter_returns200() throws Exception {
        AdminService adminService = mock(AdminService.class);

        when(adminService.getComplaints(
                Status.PENDING, null, null, null))
                .thenReturn(List.of());

        mockMvc(adminService)
                .perform(get("/api/admin/complaints")
                        .param("status", "PENDING"))
                .andExpect(status().isOk());

        verify(adminService)
                .getComplaints(Status.PENDING, null, null, null);
    }

    @Test
    void getComplaints_withCategoryFilter_returns200() throws Exception {
        AdminService adminService = mock(AdminService.class);

        when(adminService.getComplaints(
                null, "Plumbing", null, null))
                .thenReturn(List.of());

        mockMvc(adminService)
                .perform(get("/api/admin/complaints")
                        .param("category", "Plumbing"))
                .andExpect(status().isOk());

        verify(adminService)
                .getComplaints(null, "Plumbing", null, null);
    }

    @Test
    void getComplaints_withDateFilter_returns200() throws Exception {
        AdminService adminService = mock(AdminService.class);

        LocalDateTime from = LocalDateTime.of(2026, 10, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 10, 6, 23, 59);

        when(adminService.getComplaints(
                null, null, from, to))
                .thenReturn(List.of());

        mockMvc(adminService)
                .perform(get("/api/admin/complaints")
                        .param("from", "2026-10-01T00:00:00")
                        .param("to", "2026-10-06T23:59:00"))
                .andExpect(status().isOk());

        verify(adminService)
                .getComplaints(null, null, from, to);
    }
}
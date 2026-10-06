package com.example.demo.hostel.controller;

import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.model.Status;
import com.example.demo.hostel.service.AdminService;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class AdminControllerTest {

    private MockMvc mockMvc(AdminService adminService) {
        return standaloneSetup(new AdminController(adminService)).build();
    }

    @Test
    void updateStatus_withValidTransition_returns200() throws Exception {
        AdminService adminService = mock(AdminService.class);

        Complaint complaint = new Complaint();
        complaint.setStatus(Status.ASSIGNED);

        when(adminService.updateStatus(1L, Status.ASSIGNED))
                .thenReturn(complaint);

        mockMvc(adminService)
                .perform(
                        patch("/api/admin/complaints/1/status")
                                .param("status", "ASSIGNED"))
                .andExpect(status().isOk());

        verify(adminService)
                .updateStatus(1L, Status.ASSIGNED);
    }

    @Test
    void updateStatus_withInvalidTransition_returns400() throws Exception {
        AdminService adminService = mock(AdminService.class);

        when(adminService.updateStatus(1L, Status.RESOLVED))
                .thenThrow(new IllegalArgumentException(
                        "Invalid status transition"));

        mockMvc(adminService)
                .perform(
                        patch("/api/admin/complaints/1/status")
                                .param("status", "RESOLVED"))
                .andExpect(status().isBadRequest());

        verify(adminService)
                .updateStatus(1L, Status.RESOLVED);
    }
}
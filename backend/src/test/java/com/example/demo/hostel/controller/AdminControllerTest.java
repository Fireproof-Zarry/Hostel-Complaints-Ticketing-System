package com.example.demo.hostel.controller;

import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.model.Status;
import com.example.demo.hostel.service.AdminService;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

        @Test
        void getComplaints_withoutFilters_returns200() throws Exception {
                AdminService adminService = mock(AdminService.class);

                when(adminService.getComplaints(
                                null, null, null, null, null, null))
                                .thenReturn(List.of());

                mockMvc(adminService)
                                .perform(get("/api/admin/complaints"))
                                .andExpect(status().isOk());

                verify(adminService)
                                .getComplaints(
                                                null, null, null, null, null, null);
        }

        @Test
        void getComplaints_withStatusFilter_returns200() throws Exception {
                AdminService adminService = mock(AdminService.class);

                when(adminService.getComplaints(
                                Status.PENDING, null, null, null, null, null))
                                .thenReturn(List.of());

                mockMvc(adminService)
                                .perform(get("/api/admin/complaints")
                                                .param("status", "PENDING"))
                                .andExpect(status().isOk());

                verify(adminService)
                                .getComplaints(
                                                Status.PENDING, null, null, null, null, null);
        }

        @Test
        void getComplaints_withCategoryFilter_returns200() throws Exception {
                AdminService adminService = mock(AdminService.class);

                when(adminService.getComplaints(
                                null, "Plumbing", null, null, null, null))
                                .thenReturn(List.of());

                mockMvc(adminService)
                                .perform(get("/api/admin/complaints")
                                                .param("category", "Plumbing"))
                                .andExpect(status().isOk());

                verify(adminService)
                                .getComplaints(
                                                null, "Plumbing", null, null, null, null);
        }

        @Test
        void getComplaints_withDateFilter_returns200() throws Exception {
                AdminService adminService = mock(AdminService.class);

                LocalDateTime from = LocalDateTime.of(2026, 10, 1, 0, 0);
                LocalDateTime to = LocalDateTime.of(2026, 10, 6, 23, 59);

                when(adminService.getComplaints(
                                null, null, null, null, from, to))
                                .thenReturn(List.of());

                mockMvc(adminService)
                                .perform(get("/api/admin/complaints")
                                                .param("from", "2026-10-01T00:00:00")
                                                .param("to", "2026-10-06T23:59:00"))
                                .andExpect(status().isOk());

                verify(adminService)
                                .getComplaints(
                                                null, null, null, null, from, to);
        }

        @Test
        void assignComplaint_withValidAdmin_returns200() throws Exception {
                AdminService adminService = mock(AdminService.class);

                Complaint complaint = new Complaint();
                complaint.setAssignedTo("admin@smail.iitm.ac.in");

                when(adminService.assignComplaint(
                        1L,
                        "admin@smail.iitm.ac.in"
                )).thenReturn(complaint);

                mockMvc(adminService)
                        .perform(
                                patch("/api/admin/complaints/1/assign")
                                        .param("email", "admin@smail.iitm.ac.in")
                        )
                        .andExpect(status().isOk());

                verify(adminService).assignComplaint(
                        1L,
                        "admin@smail.iitm.ac.in"
                );
        }

        @Test
        void assignComplaint_whenUserNotFound_returns400() throws Exception {
                AdminService adminService = mock(AdminService.class);

                when(adminService.assignComplaint(
                        1L,
                        "unknown@smail.iitm.ac.in"
                )).thenThrow(
                        new IllegalArgumentException("User not found")
                );

                mockMvc(adminService)
                        .perform(
                                patch("/api/admin/complaints/1/assign")
                                        .param("email", "unknown@smail.iitm.ac.in")
                        )
                        .andExpect(status().isBadRequest());

                verify(adminService).assignComplaint(
                        1L,
                        "unknown@smail.iitm.ac.in"
                );
        }

        @Test
        void assignComplaint_whenUserIsNotAdmin_returns400() throws Exception {
                AdminService adminService = mock(AdminService.class);

                when(adminService.assignComplaint(
                        1L,
                        "student@smail.iitm.ac.in"
                )).thenThrow(
                        new IllegalArgumentException(
                                "Complaint can only be assigned to an admin"
                        )
                );

                mockMvc(adminService)
                        .perform(
                                patch("/api/admin/complaints/1/assign")
                                        .param("email", "student@smail.iitm.ac.in")
                        )
                        .andExpect(status().isBadRequest());

                verify(adminService).assignComplaint(
                        1L,
                        "student@smail.iitm.ac.in"
                );
        }
}
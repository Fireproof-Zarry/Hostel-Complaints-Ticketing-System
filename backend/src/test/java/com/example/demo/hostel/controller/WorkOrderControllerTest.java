
package com.example.demo.hostel.controller;

import com.example.demo.hostel.model.WorkOrder;
import com.example.demo.hostel.service.WorkOrderService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:workorder-controller-test;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
@AutoConfigureMockMvc
class WorkOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WorkOrderService workOrderService;

    private static final UUID WORK_ORDER_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

    private static final String ASSIGNEE_ID = "admin@smail.iitm.ac.in";

    private RequestPostProcessor adminJwt() {
        return jwt().authorities(
                new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private RequestPostProcessor studentJwt() {
        return jwt().authorities(
                new SimpleGrantedAuthority("ROLE_STUDENT"));
    }

    @Test
    void createWorkOrder_returnsCreated() throws Exception {
        mockMvc.perform(post("/api/admin/work-orders")
                .with(adminJwt())
                .param("complaintId", "1")
                .param("assigneeId", ASSIGNEE_ID)
                .param("priority", "HIGH")
                .param("deadline", "2026-11-05T18:00:00"))
                .andExpect(status().isCreated());

        verify(workOrderService).assignWorkOrder(
                1L, ASSIGNEE_ID, "HIGH",
                java.time.LocalDateTime.parse("2026-11-05T18:00:00"));
    }

    @Test
    void createWorkOrder_missingRequiredParameter_returnsBadRequest()
            throws Exception {
        mockMvc.perform(post("/api/admin/work-orders")
                .with(adminJwt())
                .param("complaintId", "1")
                .param("assigneeId", ASSIGNEE_ID))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAllWorkOrders_returnsOk() throws Exception {
        when(workOrderService.getAllWorkOrders())
                .thenReturn(List.of());

        mockMvc.perform(get("/api/admin/work-orders")
                .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(workOrderService).getAllWorkOrders();
    }

    @Test
    void getWorkOrder_returnsOk() throws Exception {
        mockMvc.perform(get("/api/admin/work-orders/{id}", WORK_ORDER_ID)
                .with(adminJwt()))
                .andExpect(status().isOk());

        verify(workOrderService).getWorkOrder(WORK_ORDER_ID);
    }

    @Test
    void getWorkOrdersByAssignee_returnsOk() throws Exception {
        when(workOrderService.getWorkOrdersByAssignee(ASSIGNEE_ID))
                .thenReturn(List.of());

        mockMvc.perform(get(
                "/api/admin/work-orders/assignee/{assigneeId}",
                ASSIGNEE_ID)
                .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(workOrderService).getWorkOrdersByAssignee(ASSIGNEE_ID);
    }

    @Test
    void updateWorkOrderStatus_returnsOk() throws Exception {
        mockMvc.perform(patch(
                "/api/admin/work-orders/{id}/status", WORK_ORDER_ID)
                .with(adminJwt())
                .param("status", "IN_PROGRESS"))
                .andExpect(status().isOk());

        verify(workOrderService).updateWorkOrderStatus(
                WORK_ORDER_ID, "IN_PROGRESS", null);
    }

    @Test
    void updateWorkOrderStatus_withCompletionNotes_returnsOk()
            throws Exception {
        mockMvc.perform(patch(
                "/api/admin/work-orders/{id}/status", WORK_ORDER_ID)
                .with(adminJwt())
                .param("status", "COMPLETED")
                .param("completionNotes", "Fan replaced"))
                .andExpect(status().isOk());

        verify(workOrderService).updateWorkOrderStatus(
                WORK_ORDER_ID, "COMPLETED", "Fan replaced");
    }

    @Test
    void updateWorkOrderStatus_missingStatus_returnsBadRequest()
            throws Exception {
        mockMvc.perform(patch(
                "/api/admin/work-orders/{id}/status", WORK_ORDER_ID)
                .with(adminJwt()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void studentCannotAccessWorkOrders() throws Exception {
        mockMvc.perform(get("/api/admin/work-orders")
                .with(studentJwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedUserCannotAccessWorkOrders() throws Exception {
        mockMvc.perform(get("/api/admin/work-orders"))
                .andExpect(status().isUnauthorized());
    }
}

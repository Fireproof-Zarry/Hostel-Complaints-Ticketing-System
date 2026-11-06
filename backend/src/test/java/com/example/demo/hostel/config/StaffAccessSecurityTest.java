package com.example.demo.hostel.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** URL-level access for STUDENT / STAFF / ADMIN through the real security filter chain. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:staff-access-test",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureMockMvc
class StaffAccessSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void studentsCannotUseAdminOrWorkOrderEndpoints() throws Exception {
        mockMvc.perform(get("/api/admin/complaints").with(as("ROLE_STUDENT"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/work-orders/me").with(as("ROLE_STUDENT"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/staff").with(as("ROLE_STUDENT"))).andExpect(status().isForbidden());
    }

    @Test
    void staffShareTheAdminComplaintViewButNotStaffManagement() throws Exception {
        mockMvc.perform(get("/api/admin/complaints").with(as("ROLE_STAFF"))).andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/complaints/archived").with(as("ROLE_STAFF"))).andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/staff").with(as("ROLE_STAFF"))).andExpect(status().isForbidden());
    }

    @Test
    void adminsCanManageStaff() throws Exception {
        mockMvc.perform(get("/api/admin/staff").with(as("ROLE_ADMIN"))).andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/complaints").with(as("ROLE_ADMIN"))).andExpect(status().isOk());
    }

    @Test
    void rejectedRequestsCarryAReasonTheFrontendCanShow() throws Exception {
        mockMvc.perform(post("/api/admin/staff").with(as("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Enter a valid email address"));
    }

    private static JwtRequestPostProcessor as(String role) {
        return jwt().jwt(token -> token.claim("email", "someone@example.com"))
                .authorities(new SimpleGrantedAuthority(role));
    }
}

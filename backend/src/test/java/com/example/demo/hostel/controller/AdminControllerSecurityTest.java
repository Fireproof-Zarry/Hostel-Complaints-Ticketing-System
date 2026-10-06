package com.example.demo.hostel.controller;

import com.example.demo.hostel.config.CustomJwtAuthenticationConverter;
import com.example.demo.hostel.config.SecurityConfig;
import com.example.demo.hostel.service.AdminService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminController.class)
@Import(SecurityConfig.class)
@ImportAutoConfiguration({
        SecurityAutoConfiguration.class,
        ServletWebSecurityAutoConfiguration.class
})
class AdminControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminService adminService;

    @MockitoBean
    private CustomJwtAuthenticationConverter jwtAuthenticationConverter;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void whenNoTokenProvided_thenReturns401() throws Exception {

        mockMvc.perform(
                get("/api/admin/complaints")
        ).andExpect(status().isUnauthorized());
    }

    @Test
    void whenStudentAccessesAdminEndpoint_thenReturns403() throws Exception {

        mockMvc.perform(
                get("/api/admin/complaints")
                        .with(jwt().authorities(
                                new SimpleGrantedAuthority("ROLE_STUDENT")
                        ))
        ).andExpect(status().isForbidden());
    }

    @Test
    void whenAdminAccessesAdminEndpoint_thenReturns200() throws Exception {

        when(adminService.getComplaints(
                null, null, null, null
        )).thenReturn(List.of());

        mockMvc.perform(
                get("/api/admin/complaints")
                        .with(jwt().authorities(
                                new SimpleGrantedAuthority("ROLE_ADMIN")
                        ))
        ).andExpect(status().isOk());
    }
}
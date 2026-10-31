package com.example.demo.hostel.controller;

import com.example.demo.hostel.config.SecurityConfig;
import com.example.demo.hostel.repository.UserRepository;
import com.example.demo.hostel.service.AuthService;
import com.example.demo.hostel.service.StudentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({StudentController.class, HealthController.class})
@Import(SecurityConfig.class)
@ImportAutoConfiguration({SecurityAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class})
public class StudentControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StudentService studentService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    public void whenNoTokenProvided_thenReturns401() throws Exception {
        mockMvc.perform(get("/api/student/complaints"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void whenInvalidTokenProvided_thenReturns401() throws Exception {
        when(jwtDecoder.decode(anyString())).thenThrow(new BadJwtException("Invalid token"));

        mockMvc.perform(get("/api/student/complaints")
                .header("Authorization", "Bearer some-fake-invalid-token"))
                .andExpect(status().isUnauthorized());
    }
    
    @Test
    public void rejectedTokenReasonIsReadableByTheFrontend() throws Exception {
        when(jwtDecoder.decode(anyString())).thenThrow(new BadJwtException("Jwt expired at 2026-11-01T10:00:00Z"));

        // Cross-origin, as the Vite frontend (localhost:5173) calls the API
        mockMvc.perform(get("/api/student/complaints")
                .header("Origin", "http://localhost:5173")
                .header("Authorization", "Bearer expired-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("Jwt expired")))
                .andExpect(header().string("Access-Control-Expose-Headers", containsString("WWW-Authenticate")));
    }

    @Test
    public void whenHealthCheck_thenReturns200() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk());
    }
}
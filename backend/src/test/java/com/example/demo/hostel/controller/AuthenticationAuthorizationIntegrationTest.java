package com.example.demo.hostel.controller;

import com.example.demo.hostel.config.CustomJwtAuthenticationConverter;
import com.example.demo.hostel.config.SecurityConfig;
import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.model.User;
import com.example.demo.hostel.repository.UserRepository;
import com.example.demo.hostel.service.AdminService;
import com.example.demo.hostel.service.AuthService;
import com.example.demo.hostel.service.StudentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({AdminController.class, StudentController.class})
@Import({
        SecurityConfig.class,
        CustomJwtAuthenticationConverter.class,
        AuthenticationAuthorizationIntegrationTest.AuthConfiguration.class
})
@ImportAutoConfiguration({
        SecurityAutoConfiguration.class,
        ServletWebSecurityAutoConfiguration.class
})
class AuthenticationAuthorizationIntegrationTest {

    private static final String STUDENT_EMAIL = "student@smail.iitm.ac.in";
    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final String UNAUTHORIZED_EMAIL = "unauthorized@example.com";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminService adminService;

    @MockitoBean
    private StudentService studentService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private final Map<String, User> users = new ConcurrentHashMap<>();
    private final Map<String, Jwt> tokens = Map.of(
            "student-token", jwt("student-token", STUDENT_EMAIL, true, "smail.iitm.ac.in"),
            "admin-token", jwt("admin-token", ADMIN_EMAIL, true, null),
            "unauthorized-token", jwt("unauthorized-token", UNAUTHORIZED_EMAIL, true, null),
            "unverified-token", jwt("unverified-token", STUDENT_EMAIL, false, "smail.iitm.ac.in")
    );

    @BeforeEach
    void configureAuthenticationFixtures() {
        users.clear();
        when(userRepository.findByEmail(anyString()))
                .thenAnswer(invocation -> java.util.Optional.ofNullable(users.get(invocation.getArgument(0))));
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> {
                    User user = invocation.getArgument(0);
                    users.put(user.getEmail(), user);
                    return user;
                });
        when(jwtDecoder.decode(anyString()))
                .thenAnswer(invocation -> tokens.get(invocation.getArgument(0)));
        when(adminService.getComplaints(
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull()
        )).thenReturn(List.of());
        when(studentService.getStudentComplaints(any(User.class))).thenReturn(List.<Complaint>of());
    }

    @Test
    void verifiedStudentCanAccessStudentEndpointButNotAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/student/complaints")
                        .header("Authorization", "Bearer student-token"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/complaints")
                        .header("Authorization", "Bearer student-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void configuredAdminCanAccessAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/admin/complaints")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk());
    }

    @Test
    void unauthorizedGoogleAccountIsRejected() throws Exception {
        mockMvc.perform(get("/api/student/complaints")
                        .header("Authorization", "Bearer unauthorized-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unverifiedGoogleEmailIsRejected() throws Exception {
        mockMvc.perform(get("/api/student/complaints")
                        .header("Authorization", "Bearer unverified-token"))
                .andExpect(status().isUnauthorized());
    }

    private static Jwt jwt(String token, String email, boolean verified, String hostedDomain) {
        Jwt.Builder builder = Jwt.withTokenValue(token)
                .header("alg", "none")
                .claim("email", email)
                .claim("email_verified", verified)
                .claim("name", email);
        if (hostedDomain != null) {
            builder.claim("hd", hostedDomain);
        }
        return builder.build();
    }

    @TestConfiguration
    static class AuthConfiguration {
        @Bean
        AuthService authService(UserRepository userRepository) {
            return new AuthService(userRepository, ADMIN_EMAIL);
        }
    }
}

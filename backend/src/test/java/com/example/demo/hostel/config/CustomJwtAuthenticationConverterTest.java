package com.example.demo.hostel.config;

import com.example.demo.hostel.model.Role;
import com.example.demo.hostel.model.User;
import com.example.demo.hostel.service.AuthService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomJwtAuthenticationConverterTest {

    @Mock
    private AuthService authService;

    @Mock
    private Jwt jwt;

    @Test
    void whenUserIsAdmin_thenConverterAddsAdminAuthority() {

        User user = new User();
        user.setEmail("admin@smail.iitm.ac.in");
        user.setRole(Role.ADMIN);

        when(authService.processGoogleLogin(jwt)).thenReturn(user);

        CustomJwtAuthenticationConverter converter =
                new CustomJwtAuthenticationConverter(authService);

        Authentication authentication = converter.convert(jwt);

        assertTrue(
                authentication.getAuthorities().stream()
                        .anyMatch(authority ->
                                authority.getAuthority().equals("ROLE_ADMIN"))
        );

        verify(authService).processGoogleLogin(jwt);
    }

    @Test
    void whenUserIsStudent_thenConverterAddsStudentAuthority() {

        User user = new User();
        user.setEmail("student@smail.iitm.ac.in");
        user.setRole(Role.STUDENT);

        when(authService.processGoogleLogin(jwt)).thenReturn(user);

        CustomJwtAuthenticationConverter converter =
                new CustomJwtAuthenticationConverter(authService);

        Authentication authentication = converter.convert(jwt);

        assertTrue(
                authentication.getAuthorities().stream()
                        .anyMatch(authority ->
                                authority.getAuthority().equals("ROLE_STUDENT"))
        );

        verify(authService).processGoogleLogin(jwt);
    }
}
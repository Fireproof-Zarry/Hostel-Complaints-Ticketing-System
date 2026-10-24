package com.example.demo.hostel.controller;

import com.example.demo.hostel.config.SecurityConfig;
import com.example.demo.hostel.dto.ProfileUpdateRequest;
import com.example.demo.hostel.model.User;
import com.example.demo.hostel.repository.UserRepository;
import com.example.demo.hostel.service.AuthService;
import com.example.demo.hostel.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
@ImportAutoConfiguration({SecurityAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class})
class UserControllerSecurityTest {

    private static final String EMAIL = "student@smail.iitm.ac.in";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void savesTokenFromJsonBody() throws Exception {
        mockMvc.perform(put("/api/users/u1/fcm-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"device-token\"}")
                        .with(jwt().jwt(token -> token.claim("email", EMAIL))))
                .andExpect(status().isNoContent());

        verify(userService).updateFcmToken("u1", EMAIL, "device-token");
    }

    @Test
    void savesTokenFromQueryParam() throws Exception {
        mockMvc.perform(put("/api/users/u1/fcm-token")
                        .param("token", "device-token")
                        .with(jwt().jwt(token -> token.claim("email", EMAIL))))
                .andExpect(status().isNoContent());

        verify(userService).updateFcmToken("u1", EMAIL, "device-token");
    }

    @Test
    void otherUsersTokenIsForbidden() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
                .when(userService).updateFcmToken("someone-else", EMAIL, "device-token");

        mockMvc.perform(put("/api/users/someone-else/fcm-token")
                        .param("token", "device-token")
                        .with(jwt().jwt(token -> token.claim("email", EMAIL))))
                .andExpect(status().isForbidden());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(put("/api/users/u1/fcm-token").param("token", "device-token"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userService);
    }

    @Test
    void clearsToken() throws Exception {
        mockMvc.perform(delete("/api/users/u1/fcm-token")
                        .with(jwt().jwt(token -> token.claim("email", EMAIL))))
                .andExpect(status().isNoContent());

        verify(userService).clearFcmToken("u1", EMAIL);
    }

    @Test
    void updatesOwnProfileAcceptingSnakeCaseFields() throws Exception {
        User saved = new User();
        saved.setId("u1");
        saved.setFullName("Ragini Z");
        saved.setRoomNumber("312");
        saved.setFcmToken("device-token");
        ProfileUpdateRequest expected = new ProfileUpdateRequest("Ragini Z", "9876543210", "CS23B051", "312");
        when(userService.updateProfile("u1", EMAIL, expected)).thenReturn(saved);

        mockMvc.perform(put("/api/users/u1/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"full_name\":\"Ragini Z\",\"phone_number\":\"9876543210\","
                                + "\"roll_number\":\"CS23B051\",\"room_number\":\"312\"}")
                        .with(jwt().jwt(token -> token.claim("email", EMAIL))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Ragini Z"))
                .andExpect(jsonPath("$.roomNumber").value("312"))
                .andExpect(jsonPath("$.fcmToken").doesNotExist());
    }

    @Test
    void anotherUsersProfileIsForbidden() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
                .when(userService).updateProfile(eq("someone-else"),
                        eq(EMAIL), any());

        mockMvc.perform(put("/api/users/someone-else/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Hacked\"}")
                        .with(jwt().jwt(token -> token.claim("email", EMAIL))))
                .andExpect(status().isForbidden());
    }
}

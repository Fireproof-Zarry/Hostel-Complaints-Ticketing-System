package com.example.demo.hostel.controller;

import com.example.demo.hostel.config.SecurityConfig;
import com.example.demo.hostel.repository.UserRepository;
import com.example.demo.hostel.service.AuthService;
import com.example.demo.hostel.service.ComplaintService;
import com.example.demo.hostel.service.UpvoteService;
import com.example.demo.hostel.validation.RoomLocationValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ComplaintController.class)
@Import({SecurityConfig.class, RoomLocationValidator.class})
@ImportAutoConfiguration({SecurityAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class})
class ComplaintUpvoteSecurityTest {

    private static final String EMAIL = "neighbour@smail.iitm.ac.in";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UpvoteService upvoteService;

    @MockitoBean
    private ComplaintService complaintService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void returnsNewUpvoteStatusAndCount() throws Exception {
        when(upvoteService.userIdForEmail(EMAIL)).thenReturn("u2");
        when(upvoteService.toggleUpvote(7L, "u2")).thenReturn(true);
        when(upvoteService.countUpvotes(7L)).thenReturn(3L);

        mockMvc.perform(post("/api/complaints/7/upvote")
                        .with(jwt().jwt(token -> token.claim("email", EMAIL))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complaintId").value(7))
                .andExpect(jsonPath("$.upvoted").value(true))
                .andExpect(jsonPath("$.upvoteCount").value(3));
    }

    @Test
    void removedUpvoteReturnsFalse() throws Exception {
        when(upvoteService.userIdForEmail(EMAIL)).thenReturn("u2");
        when(upvoteService.toggleUpvote(7L, "u2")).thenReturn(false);
        when(upvoteService.countUpvotes(7L)).thenReturn(2L);

        mockMvc.perform(post("/api/complaints/7/upvote")
                        .with(jwt().jwt(token -> token.claim("email", EMAIL))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upvoted").value(false))
                .andExpect(jsonPath("$.upvoteCount").value(2));
    }

    @Test
    void doubleClickRaceIsReportedAsUpvoted() throws Exception {
        when(upvoteService.userIdForEmail(EMAIL)).thenReturn("u2");
        when(upvoteService.toggleUpvote(7L, "u2")).thenThrow(new DataIntegrityViolationException("duplicate key"));
        when(upvoteService.countUpvotes(7L)).thenReturn(1L);

        mockMvc.perform(post("/api/complaints/7/upvote")
                        .with(jwt().jwt(token -> token.claim("email", EMAIL))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upvoted").value(true))
                .andExpect(jsonPath("$.upvoteCount").value(1));
    }

    @Test
    void ruleViolationsKeepTheirStatus() throws Exception {
        when(upvoteService.userIdForEmail(EMAIL)).thenReturn("u2");
        when(upvoteService.toggleUpvote(7L, "u2"))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot upvote your own complaint"));

        mockMvc.perform(post("/api/complaints/7/upvote")
                        .with(jwt().jwt(token -> token.claim("email", EMAIL))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/complaints/7/upvote"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(upvoteService);
    }
}

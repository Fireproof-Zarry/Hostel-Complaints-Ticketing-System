package com.example.demo.hostel.controller;

import com.example.demo.hostel.config.SecurityConfig;
import com.example.demo.hostel.dto.DuplicateCandidate;
import com.example.demo.hostel.model.Status;
import com.example.demo.hostel.repository.UserRepository;
import com.example.demo.hostel.service.AuthService;
import com.example.demo.hostel.service.CommunityComplaintService;
import com.example.demo.hostel.service.ComplaintService;
import com.example.demo.hostel.service.DuplicateDetectionService;
import com.example.demo.hostel.service.NudgeService;
import com.example.demo.hostel.service.UpvoteService;
import com.example.demo.hostel.validation.RoomLocationValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ComplaintController.class)
@Import({SecurityConfig.class, RoomLocationValidator.class})
@ImportAutoConfiguration({SecurityAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class})
class PotentialDuplicatesControllerTest {

    private static final String EMAIL = "neighbour@smail.iitm.ac.in";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NudgeService nudgeService;

    @MockitoBean
    private DuplicateDetectionService duplicateDetectionService;

    @MockitoBean
    private CommunityComplaintService communityComplaintService;

    @MockitoBean
    private ComplaintService complaintService;

    @MockitoBean
    private UpvoteService upvoteService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void returnsCandidatesWithoutStudentDetails() throws Exception {
        when(duplicateDetectionService.findPotentialDuplicates(EMAIL, "Dirty washroom", true, "First", "East wing", null))
                .thenReturn(List.of(new DuplicateCandidate(
                        7L, "Washroom not cleaned", Status.PENDING, "First", "East wing washroom", null,
                        3L, LocalDateTime.of(2026, 10, 30, 9, 0), false, true)));

        mockMvc.perform(get("/api/complaints/potential-duplicates")
                        .param("title", "Dirty washroom")
                        .param("floor", "First")
                        .param("wing", "East wing")
                        .with(jwt().jwt(token -> token.claim("email", EMAIL))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].title").value("Washroom not cleaned"))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].upvoteCount").value(3))
                .andExpect(jsonPath("$[0].reportedByYou").value(false))
                .andExpect(jsonPath("$[0].upvotedByYou").value(true))
                .andExpect(jsonPath("$[0].student").doesNotExist());
    }

    @Test
    void wingIsOptional() throws Exception {
        when(duplicateDetectionService.findPotentialDuplicates(EMAIL, "Dirty washroom", true, "First", null, null))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/complaints/potential-duplicates")
                        .param("title", "Dirty washroom")
                        .param("floor", "First")
                        .with(jwt().jwt(token -> token.claim("email", EMAIL))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void communityAreaWithoutFloorIsRejectedByTheService() throws Exception {
        when(duplicateDetectionService.findPotentialDuplicates(EMAIL, "Dirty washroom", true, null, null, null))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid floor is required"));

        mockMvc.perform(get("/api/complaints/potential-duplicates")
                        .param("title", "Dirty washroom")
                        .with(jwt().jwt(token -> token.claim("email", EMAIL))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void personalIssueNeedsNoLocation() throws Exception {
        when(duplicateDetectionService.findPotentialDuplicates(EMAIL, "Fan not working", false, null, null, null))
                .thenReturn(List.of(new DuplicateCandidate(
                        31L, "Ceiling fan not working", Status.PENDING, "First", null, "204",
                        0L, LocalDateTime.of(2026, 10, 30, 9, 0), true, false)));

        mockMvc.perform(get("/api/complaints/potential-duplicates")
                        .param("title", "Fan not working")
                        .param("communityArea", "false")
                        .with(jwt().jwt(token -> token.claim("email", EMAIL))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].room").value("204"))
                .andExpect(jsonPath("$[0].reportedByYou").value(true));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/complaints/potential-duplicates")
                        .param("title", "Dirty washroom")
                        .param("floor", "First"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(duplicateDetectionService);
    }
}

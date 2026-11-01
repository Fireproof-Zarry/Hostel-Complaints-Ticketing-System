package com.example.demo.hostel.controller;

import com.example.demo.hostel.config.SecurityConfig;
import com.example.demo.hostel.dto.CommunityComplaintPage;
import com.example.demo.hostel.dto.CommunityComplaintView;
import com.example.demo.hostel.model.Status;
import com.example.demo.hostel.repository.UserRepository;
import com.example.demo.hostel.service.AuthService;
import com.example.demo.hostel.service.CommunityComplaintService;
import com.example.demo.hostel.service.ComplaintService;
import com.example.demo.hostel.service.DuplicateDetectionService;
import com.example.demo.hostel.service.UpvoteService;
import com.example.demo.hostel.validation.RoomLocationValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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
class CommunityComplaintsControllerTest {

    private static final String EMAIL = "viewer@smail.iitm.ac.in";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CommunityComplaintService communityComplaintService;

    @MockitoBean
    private ComplaintService complaintService;

    @MockitoBean
    private UpvoteService upvoteService;

    @MockitoBean
    private DuplicateDetectionService duplicateDetectionService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static CommunityComplaintPage onePage() {
        return new CommunityComplaintPage(List.of(new CommunityComplaintView(
                7L, "Washroom not cleaned", "Not cleaned today", Status.PENDING, "Cleaning",
                "First", "East wing washroom", null, LocalDateTime.of(2026, 11, 1, 9, 0),
                3L, false, true)), 0, 20, 1, 1);
    }

    @Test
    void defaultsToMostUpvotedFirstPage() throws Exception {
        when(communityComplaintService.getCommunityComplaints(EMAIL, null, "upvotes", 0, 20)).thenReturn(onePage());

        mockMvc.perform(get("/api/complaints/community")
                        .with(jwt().jwt(token -> token.claim("email", EMAIL))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(7))
                .andExpect(jsonPath("$.items[0].title").value("Washroom not cleaned"))
                .andExpect(jsonPath("$.items[0].category").value("Cleaning"))
                .andExpect(jsonPath("$.items[0].wing").value("East wing washroom"))
                .andExpect(jsonPath("$.items[0].upvoteCount").value(3))
                .andExpect(jsonPath("$.items[0].upvotedByYou").value(true))
                .andExpect(jsonPath("$.items[0].reportedByYou").value(false))
                .andExpect(jsonPath("$.items[0].student").doesNotExist())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void passesFloorSortAndPaging() throws Exception {
        when(communityComplaintService.getCommunityComplaints(EMAIL, "Second", "recent", 2, 10)).thenReturn(onePage());

        mockMvc.perform(get("/api/complaints/community")
                        .param("floor", "Second")
                        .param("sort", "recent")
                        .param("page", "2")
                        .param("size", "10")
                        .with(jwt().jwt(token -> token.claim("email", EMAIL))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/complaints/community"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(communityComplaintService);
    }
}

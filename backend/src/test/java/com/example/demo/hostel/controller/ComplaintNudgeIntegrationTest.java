package com.example.demo.hostel.controller;

import com.example.demo.hostel.model.Category;
import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.model.Location;
import com.example.demo.hostel.model.Role;
import com.example.demo.hostel.model.Status;
import com.example.demo.hostel.model.User;
import com.example.demo.hostel.repository.CategoryRepository;
import com.example.demo.hostel.repository.ComplaintRepository;
import com.example.demo.hostel.repository.LocationRepository;
import com.example.demo.hostel.repository.UserRepository;
import com.example.demo.hostel.service.AdminService;
import com.example.demo.hostel.service.FcmNotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:complaint-nudge-test",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureMockMvc
class ComplaintNudgeIntegrationTest {

    private static final String OWNER_EMAIL = "owner@smail.iitm.ac.in";
    private static final String OTHER_EMAIL = "other@smail.iitm.ac.in";

    @MockitoBean private FcmNotificationService fcmNotificationService;

    @Autowired private MockMvc mockMvc;
    @Autowired private AdminService adminService;
    @Autowired private ComplaintRepository complaintRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private User owner;
    private Category category;
    private Location room;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(user(OWNER_EMAIL, Role.STUDENT, null));
        userRepository.save(user(OTHER_EMAIL, Role.STUDENT, "student-token"));
        userRepository.save(user("admin1@smail.iitm.ac.in", Role.ADMIN, "admin-token-1"));
        userRepository.save(user("admin2@smail.iitm.ac.in", Role.ADMIN, "admin-token-2"));
        userRepository.save(user("admin3@smail.iitm.ac.in", Role.ADMIN, null));
        userRepository.save(user("staff@smail.iitm.ac.in", Role.STAFF, "staff-token"));

        category = new Category();
        category.setName("Plumbing");
        category.setDefaultSlaHours(48);
        category = categoryRepository.save(category);

        room = new Location();
        room.setFloor("Floor 2");
        room.setRoomNumber("204");
        room = locationRepository.save(room);
    }

    @AfterEach
    void tearDown() {
        complaintRepository.deleteAll();
        locationRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void tooEarlyNudgeIsRejectedThenAnOlderUnresolvedComplaintNotifiesExactlyOnce() throws Exception {
        Complaint recent = complaintAged(6, Status.PENDING);
        Complaint old = complaintAged(8, Status.PENDING);

        nudge(recent, OWNER_EMAIL).andExpect(status().isConflict());
        verify(fcmNotificationService, never()).sendNudgeNotification(anyList(), any(), any());

        nudge(old, OWNER_EMAIL)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complaintId").value(old.getId()))
                .andExpect(jsonPath("$.lastNudgedAt").exists())
                .andExpect(jsonPath("$.nextNudgeAt").exists());

        verify(fcmNotificationService, times(1)).sendNudgeNotification(
                argThatHasTokens("admin-token-1", "admin-token-2"), eq(old.getId()), eq("Leaking tap"));
    }

    @Test
    void repeatWithinSevenDaysIsRejectedAndSendsNothingMore() throws Exception {
        Complaint old = complaintAged(20, Status.ASSIGNED);

        nudge(old, OWNER_EMAIL).andExpect(status().isOk());
        nudge(old, OWNER_EMAIL).andExpect(status().isConflict());

        verify(fcmNotificationService, times(1)).sendNudgeNotification(anyList(), any(), any());
    }

    @Test
    void canNudgeAgainOnceTheLastNudgeIsOverSevenDaysOld() throws Exception {
        Complaint old = complaintAged(20, Status.IN_PROGRESS);
        lastNudged(old, LocalDateTime.now().minusDays(8));

        nudge(old, OWNER_EMAIL).andExpect(status().isOk());
    }

    @Test
    void closedComplaintsAreRejected() throws Exception {
        nudge(complaintAged(20, Status.RESOLVED), OWNER_EMAIL).andExpect(status().isConflict());
        nudge(complaintAged(20, Status.REJECTED), OWNER_EMAIL).andExpect(status().isConflict());

        verify(fcmNotificationService, never()).sendNudgeNotification(anyList(), any(), any());
    }

    @Test
    void onlyTheOwnerCanNudge() throws Exception {
        nudge(complaintAged(20, Status.PENDING), OTHER_EMAIL).andExpect(status().isNotFound());

        verify(fcmNotificationService, never()).sendNudgeNotification(anyList(), any(), any());
    }

    @Test
    void missingComplaintIsNotFound() throws Exception {
        mockMvc.perform(post("/api/complaints/{id}/nudge", 999_999L).with(user(OWNER_EMAIL)))
                .andExpect(status().isNotFound());
    }

    @Test
    void myComplaintsTellTheOwnerWhetherTheyCanNudge() throws Exception {
        Complaint recent = complaintAged(2, Status.PENDING);
        Complaint old = complaintAged(9, Status.PENDING);
        Complaint closed = complaintAged(9, Status.RESOLVED);

        mockMvc.perform(get("/api/complaints/mine").with(user(OWNER_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == %d)].canNudge".formatted(recent.getId())).value(false))
                .andExpect(jsonPath("$[?(@.id == %d)].nextNudgeAt".formatted(recent.getId())).isNotEmpty())
                .andExpect(jsonPath("$[?(@.id == %d)].canNudge".formatted(old.getId())).value(true))
                .andExpect(jsonPath("$[?(@.id == %d)].canNudge".formatted(closed.getId())).value(false));
    }

    @Test
    void adminListPutsUnresolvedNudgedComplaintsFirstLatestNudgeOnTop() {
        Complaint plain = complaintAged(30, Status.PENDING);
        Complaint nudgedEarlier = complaintAged(20, Status.PENDING);
        Complaint nudgedLater = complaintAged(10, Status.IN_PROGRESS);
        Complaint nudgedThenResolved = complaintAged(25, Status.RESOLVED);
        lastNudged(nudgedEarlier, LocalDateTime.now().minusDays(2));
        lastNudged(nudgedLater, LocalDateTime.now().minusDays(1));
        lastNudged(nudgedThenResolved, LocalDateTime.now());

        List<Long> ids = adminService.getComplaints(null, null, null, null, null, null, null).stream()
                .map(Complaint::getId)
                .toList();

        assertThat(ids.subList(0, 2)).containsExactly(nudgedLater.getId(), nudgedEarlier.getId());
        assertThat(ids).containsExactlyInAnyOrder(
                plain.getId(), nudgedEarlier.getId(), nudgedLater.getId(), nudgedThenResolved.getId());
    }

    @Test
    void nudgeLeavesUpdatedAtAndVersionAloneAndSurvivesStaleSave() throws Exception {
        Complaint old = complaintAged(20, Status.PENDING);
        Complaint loadedBeforeNudge = complaintRepository.findById(old.getId()).orElseThrow();

        nudge(old, OWNER_EMAIL).andExpect(status().isOk());

        Complaint nudged = complaintRepository.findById(old.getId()).orElseThrow();
        assertThat(nudged.getLastNudgedAt()).isNotNull();
        assertThat(nudged.getVersion()).isEqualTo(loadedBeforeNudge.getVersion());
        assertThat(nudged.getUpdatedAt()).isEqualTo(loadedBeforeNudge.getUpdatedAt());

        // An admin's request that loaded the complaint just before the nudge
        loadedBeforeNudge.setAssignedTo("Warden");
        complaintRepository.save(loadedBeforeNudge);

        assertThat(complaintRepository.findById(old.getId()).orElseThrow().getLastNudgedAt())
                .isEqualTo(nudged.getLastNudgedAt());
    }

    private static List<String> argThatHasTokens(String... tokens) {
        return org.mockito.ArgumentMatchers.argThat(actual ->
                actual != null && actual.size() == tokens.length && actual.containsAll(List.of(tokens)));
    }

    private ResultActions nudge(Complaint complaint, String email) throws Exception {
        return mockMvc.perform(post("/api/complaints/{id}/nudge", complaint.getId()).with(user(email)));
    }

    private static SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor user(String email) {
        return jwt().jwt(token -> token.claim("email", email));
    }

    private Complaint complaintAged(int days, Status status) {
        Complaint complaint = new Complaint();
        complaint.setTitle("Leaking tap");
        complaint.setDescription("Water everywhere");
        complaint.setCategory(category);
        complaint.setLocation(room);
        complaint.setStudent(owner);
        complaint.setStatus(status);
        complaint = complaintRepository.save(complaint);
        // created_at is set by Hibernate and not updatable, so age it directly
        jdbcTemplate.update("UPDATE complaints SET created_at = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusDays(days)), complaint.getId());
        return complaint;
    }

    private void lastNudged(Complaint complaint, LocalDateTime when) {
        jdbcTemplate.update("UPDATE complaints SET last_nudged_at = ? WHERE id = ?",
                Timestamp.valueOf(when), complaint.getId());
    }

    private static User user(String email, Role role, String fcmToken) {
        User user = new User();
        user.setEmail(email);
        user.setName(email);
        user.setRole(role);
        user.setFcmToken(fcmToken);
        return user;
    }
}

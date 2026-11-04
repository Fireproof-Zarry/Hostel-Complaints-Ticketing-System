
package com.example.demo.hostel.controller;

import com.example.demo.hostel.dto.analytics.AnalyticsResponseDTO;
import com.example.demo.hostel.model.Category;
import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.model.ComplaintHistory;
import com.example.demo.hostel.model.Location;
import com.example.demo.hostel.model.Role;
import com.example.demo.hostel.model.Status;
import com.example.demo.hostel.model.User;
import com.example.demo.hostel.repository.CategoryRepository;
import com.example.demo.hostel.repository.ComplaintHistoryRepository;
import com.example.demo.hostel.repository.ComplaintRepository;
import com.example.demo.hostel.repository.LocationRepository;
import com.example.demo.hostel.repository.UserRepository;
import com.example.demo.hostel.service.FcmNotificationService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.json.JsonMapper;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.sql.init.mode=never"
})
@AutoConfigureMockMvc
class AnalyticsServiceIntegrationTest {

    private static final String ADMIN_EMAIL = "admin@smail.iitm.ac.in";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ComplaintRepository complaintRepository;

    @Autowired
    private ComplaintHistoryRepository complaintHistoryRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private FcmNotificationService fcmNotificationService;

    private User student;
    private User admin;

    private Category plumbing;
    private Location firstFloorRoom;

    @BeforeEach
    void setUp() {
        student = userRepository.save(
                user("student@smail.iitm.ac.in", Role.STUDENT));

        admin = userRepository.save(
                user(ADMIN_EMAIL, Role.ADMIN));

        plumbing = category("Plumbing");
        firstFloorRoom = location("First", "204");
    }

    @AfterEach
    void tearDown() {
        // Delete in foreign-key dependency order.
        jdbcTemplate.execute("DELETE FROM complaint_history");
        jdbcTemplate.execute("DELETE FROM complaints");
        jdbcTemplate.execute("DELETE FROM categories");
        jdbcTemplate.execute("DELETE FROM locations");
        jdbcTemplate.execute("DELETE FROM users");
    }

    // ---------------------------------------------------------
    // 1. Complete analytics flow
    // ---------------------------------------------------------

    @Test
    void getAnalytics_returnsCompleteAnalyticsResponse() throws Exception {

        // One pending complaint.
        createComplaint(
                "Broken tap",
                plumbing,
                firstFloorRoom,
                Status.PENDING,
                LocalDateTime.of(2026, 10, 2, 10, 0));

        // One resolved complaint.
        Complaint resolved = createComplaint(
                "Leaking pipe",
                plumbing,
                firstFloorRoom,
                Status.RESOLVED,
                LocalDateTime.of(2026, 10, 1, 10, 0));

        // Resolution happened 48 hours after creation.
        saveHistory(
                resolved,
                Status.PENDING,
                Status.RESOLVED,
                LocalDateTime.of(2026, 10, 3, 10, 0));

        MvcResult result = mockMvc.perform(
                get("/api/admin/analytics")
                        .param("startDate", "2026-10-01")
                        .param("endDate", "2026-10-31")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andReturn();

        AnalyticsResponseDTO response = readResponse(result);

        // Overview.
        assertThat(response.getOverview().getTotalComplaints())
                .isEqualTo(2);

        assertThat(response.getOverview().getPending())
                .isEqualTo(1);

        assertThat(response.getOverview().getResolved())
                .isEqualTo(1);

        assertThat(response.getOverview().getUnresolved())
                .isEqualTo(1);

        assertThat(response.getOverview().getResolutionRate())
                .isEqualTo(50.0);

        assertThat(response.getOverview().getAverageResolutionTimeHours())
                .isEqualTo(48.0);

        // Category breakdown.
        assertThat(response.getCategoryBreakdown())
                .hasSize(1);

        assertThat(response.getCategoryBreakdown().getFirst().getCategory())
                .isEqualTo("Plumbing");

        assertThat(response.getCategoryBreakdown().getFirst().getCount())
                .isEqualTo(2);

        // Floor breakdown.
        assertThat(response.getFloorBreakdown())
                .hasSize(1);

        assertThat(response.getFloorBreakdown().getFirst().getFloor())
                .isEqualTo("First");

        assertThat(response.getFloorBreakdown().getFirst().getCount())
                .isEqualTo(2);

        // Recurring room + category combination.
        assertThat(response.getRecurringIssues())
                .hasSize(1);

        assertThat(response.getRecurringIssues().getFirst().getType())
                .isEqualTo("ROOM");

        assertThat(response.getRecurringIssues().getFirst().getRoomNumber())
                .isEqualTo("204");

        assertThat(response.getRecurringIssues().getFirst().getCount())
                .isEqualTo(2);

        // Monthly trends.
        assertThat(response.getMonthlyTrends())
                .hasSize(1);

        assertThat(response.getMonthlyTrends().getFirst().getMonth())
                .isEqualTo("2026-10");

        assertThat(response.getMonthlyTrends().getFirst().getCreated())
                .isEqualTo(2);

        assertThat(response.getMonthlyTrends().getFirst().getResolved())
                .isEqualTo(1);
    }

    // ---------------------------------------------------------
    // 2. Date filtering
    // ---------------------------------------------------------

    @Test
    void getAnalytics_appliesDateRangeCorrectly() throws Exception {

        createComplaint(
                "October complaint",
                plumbing,
                firstFloorRoom,
                Status.PENDING,
                LocalDateTime.of(2026, 10, 15, 10, 0));

        createComplaint(
                "November complaint",
                plumbing,
                firstFloorRoom,
                Status.PENDING,
                LocalDateTime.of(2026, 11, 2, 10, 0));

        MvcResult result = mockMvc.perform(
                get("/api/admin/analytics")
                        .param("startDate", "2026-10-01")
                        .param("endDate", "2026-10-31")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andReturn();

        AnalyticsResponseDTO response = readResponse(result);

        assertThat(response.getOverview().getTotalComplaints())
                .isEqualTo(1);

        assertThat(response.getOverview().getPending())
                .isEqualTo(1);

        assertThat(response.getMonthlyTrends())
                .hasSize(1);

        assertThat(response.getMonthlyTrends().getFirst().getMonth())
                .isEqualTo("2026-10");
    }

    // ---------------------------------------------------------
    // 3. Soft deletion and archival
    // ---------------------------------------------------------

    @Test
    void getAnalytics_excludesSoftDeletedButIncludesArchivedComplaints()
            throws Exception {

        // Normal complaint.
        createComplaint(
                "Normal complaint",
                plumbing,
                firstFloorRoom,
                Status.PENDING,
                LocalDateTime.now().minusDays(3));

        // Archived complaints must still be included.
        Complaint archived = createComplaint(
                "Archived complaint",
                plumbing,
                firstFloorRoom,
                Status.RESOLVED,
                LocalDateTime.now().minusDays(10));

        jdbcTemplate.update(
                "UPDATE complaints SET is_archived = TRUE WHERE id = ?",
                archived.getId());

        // Soft-deleted complaints must be excluded.
        Complaint deleted = createComplaint(
                "Deleted complaint",
                plumbing,
                firstFloorRoom,
                Status.PENDING,
                LocalDateTime.now().minusDays(2));

        jdbcTemplate.update(
                "UPDATE complaints SET deleted_at = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.now()),
                deleted.getId());

        MvcResult result = mockMvc.perform(
                get("/api/admin/analytics")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andReturn();

        AnalyticsResponseDTO response = readResponse(result);

        assertThat(response.getOverview().getTotalComplaints())
                .isEqualTo(2);

        assertThat(response.getOverview().getPending())
                .isEqualTo(1);

        assertThat(response.getOverview().getResolved())
                .isEqualTo(1);
    }

    // ---------------------------------------------------------
    // 4. Invalid input
    // ---------------------------------------------------------

    @Test
    void getAnalytics_returnsBadRequestForInvalidDateRange()
            throws Exception {

        mockMvc.perform(
                get("/api/admin/analytics")
                        .param("startDate", "2026-11-01")
                        .param("endDate", "2026-10-01")
                        .with(adminJwt()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAnalytics_returnsBadRequestForInvalidDateFormat()
            throws Exception {

        mockMvc.perform(
                get("/api/admin/analytics")
                        .param("startDate", "01-10-2026")
                        .with(adminJwt()))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------
    // Helper methods
    // ---------------------------------------------------------

    private User user(String email, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setName(email);
        user.setRole(role);
        return user;
    }

    private Category category(String name) {
        Category category = new Category();
        category.setName(name);
        category.setDefaultSlaHours(48);
        return categoryRepository.save(category);
    }

    private Location location(String floor, String roomNumber) {
        Location location = new Location();
        location.setFloor(floor);
        location.setRoomNumber(roomNumber);
        return locationRepository.save(location);
    }

    private Complaint createComplaint(
            String title,
            Category category,
            Location location,
            Status status,
            LocalDateTime createdAt) {

        Complaint complaint = new Complaint();
        complaint.setTitle(title);
        complaint.setDescription("Integration test complaint");
        complaint.setCategory(category);
        complaint.setLocation(location);
        complaint.setStudent(student);
        complaint.setStatus(status);

        Complaint saved = complaintRepository.saveAndFlush(complaint);

        // Control created_at for deterministic date-filter tests.
        jdbcTemplate.update(
                "UPDATE complaints SET created_at = ? WHERE id = ?",
                Timestamp.valueOf(createdAt),
                saved.getId());

        return saved;
    }

    private void saveHistory(
            Complaint complaint,
            Status oldStatus,
            Status newStatus,
            LocalDateTime changedAt) {

        ComplaintHistory history = new ComplaintHistory();
        history.setComplaint(complaint);
        history.setActor(admin);
        history.setOldStatus(oldStatus);
        history.setNewStatus(newStatus);

        ComplaintHistory saved = complaintHistoryRepository.saveAndFlush(history);

        // @CreationTimestamp controls the entity timestamp.
        // Update directly for deterministic historical test data.
        jdbcTemplate.update(
                "UPDATE complaint_history SET changed_at = ? WHERE history_id = ?",
                Timestamp.valueOf(changedAt),
                saved.getId());
    }

    private AnalyticsResponseDTO readResponse(MvcResult result)
            throws Exception {

        return JsonMapper.builder()
                .build()
                .readValue(
                        result.getResponse().getContentAsString(),
                        AnalyticsResponseDTO.class);
    }

    private static JwtRequestPostProcessor adminJwt() {
        return jwt()
                .jwt(token -> token.claim("email", ADMIN_EMAIL))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    // ---------------------------------------------------------
    // 5. Date-filter variations
    // ---------------------------------------------------------

    @Test
    void getAnalytics_appliesOnlyStartDateFilter() throws Exception {

        LocalDate today = LocalDate.now();

        createComplaint(
                "Older complaint",
                plumbing,
                firstFloorRoom,
                Status.PENDING,
                today.minusDays(10).atTime(10, 0));

        createComplaint(
                "Recent complaint",
                plumbing,
                firstFloorRoom,
                Status.PENDING,
                today.minusDays(2).atTime(10, 0));

        MvcResult result = mockMvc.perform(
                get("/api/admin/analytics")
                        .param("startDate", today.minusDays(5).toString())
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andReturn();

        AnalyticsResponseDTO response = readResponse(result);

        assertThat(response.getOverview().getTotalComplaints()).isEqualTo(1);
        assertThat(response.getOverview().getPending()).isEqualTo(1);
    }

    @Test
    void getAnalytics_appliesOnlyEndDateFilter() throws Exception {

        LocalDate today = LocalDate.now();

        createComplaint(
                "Older complaint",
                plumbing,
                firstFloorRoom,
                Status.PENDING,
                today.minusDays(2).atTime(10, 0));

        createComplaint(
                "Future complaint",
                plumbing,
                firstFloorRoom,
                Status.PENDING,
                today.plusDays(2).atTime(10, 0));

        MvcResult result = mockMvc.perform(
                get("/api/admin/analytics")
                        .param("endDate", today.toString())
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andReturn();

        AnalyticsResponseDTO response = readResponse(result);

        assertThat(response.getOverview().getTotalComplaints()).isEqualTo(1);
        assertThat(response.getOverview().getPending()).isEqualTo(1);
    }

    @Test
    void getAnalytics_withoutDateFiltersReturnsLatestTwelveMonths()
            throws Exception {

        MvcResult result = mockMvc.perform(
                get("/api/admin/analytics")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andReturn();

        AnalyticsResponseDTO response = readResponse(result);

        assertThat(response.getMonthlyTrends()).hasSize(12);

        String expectedFirstMonth = java.time.YearMonth.now()
                .minusMonths(11)
                .toString();

        String expectedLastMonth = java.time.YearMonth.now().toString();

        assertThat(response.getMonthlyTrends().getFirst().getMonth())
                .isEqualTo(expectedFirstMonth);

        assertThat(response.getMonthlyTrends().getLast().getMonth())
                .isEqualTo(expectedLastMonth);
    }

    // ---------------------------------------------------------
    // 6. Aggregation edge cases
    // ---------------------------------------------------------

    @Test
    void getAnalytics_includesCategoriesAndFloorsWithZeroComplaints()
            throws Exception {

        category("Electrical");
        locationRepository.save(createLocation("Second", "305"));

        createComplaint(
                "Broken tap",
                plumbing,
                firstFloorRoom,
                Status.PENDING,
                LocalDateTime.now());

        MvcResult result = mockMvc.perform(
                get("/api/admin/analytics")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andReturn();

        AnalyticsResponseDTO response = readResponse(result);

        assertThat(response.getCategoryBreakdown())
                .anySatisfy(item -> {
                    assertThat(item.getCategory()).isEqualTo("Electrical");
                    assertThat(item.getCount()).isZero();
                });

        assertThat(response.getFloorBreakdown())
                .anySatisfy(item -> {
                    assertThat(item.getFloor()).isEqualTo("Second");
                    assertThat(item.getCount()).isZero();
                });
    }

    @Test
    void getAnalytics_includesOnlyRecurringIssuesAndHandlesNullWing()
            throws Exception {

        Location communityArea = new Location();
        communityArea.setFloor("Second");
        communityArea.setRoomNumber(null);
        communityArea.setWing(null);
        communityArea.setCommunityArea(true);

        communityArea = locationRepository.save(communityArea);

        // One complaint: should not qualify as recurring.
        createComplaint(
                "Single room issue",
                plumbing,
                firstFloorRoom,
                Status.PENDING,
                LocalDateTime.now());

        // Two complaints in the same community area and category.
        createComplaint(
                "Community issue 1",
                plumbing,
                communityArea,
                Status.PENDING,
                LocalDateTime.now());

        createComplaint(
                "Community issue 2",
                plumbing,
                communityArea,
                Status.PENDING,
                LocalDateTime.now());

        MvcResult result = mockMvc.perform(
                get("/api/admin/analytics")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andReturn();

        AnalyticsResponseDTO response = readResponse(result);

        assertThat(response.getRecurringIssues()).hasSize(1);

        var issue = response.getRecurringIssues().getFirst();

        assertThat(issue.getType()).isEqualTo("COMMUNITY_AREA");
        assertThat(issue.getFloor()).isEqualTo("Second");
        assertThat(issue.getWing()).isEqualTo("UNSPECIFIED");
        assertThat(issue.getCategory()).isEqualTo("Plumbing");
        assertThat(issue.getCount()).isEqualTo(2);
    }

    @Test
    void getAnalytics_usesLatestResolutionTimestampAfterReopening()
            throws Exception {

        Complaint complaint = createComplaint(
                "Reopened complaint",
                plumbing,
                firstFloorRoom,
                Status.RESOLVED,
                LocalDateTime.of(2026, 10, 1, 10, 0));

        // First resolution.
        saveHistory(
                complaint,
                Status.IN_PROGRESS,
                Status.RESOLVED,
                LocalDateTime.of(2026, 10, 5, 10, 0));

        // Complaint reopened.
        saveHistory(
                complaint,
                Status.RESOLVED,
                Status.IN_PROGRESS,
                LocalDateTime.of(2026, 10, 10, 10, 0));

        // Second resolution.
        saveHistory(
                complaint,
                Status.IN_PROGRESS,
                Status.RESOLVED,
                LocalDateTime.of(2026, 11, 2, 10, 0));

        MvcResult result = mockMvc.perform(
                get("/api/admin/analytics")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andReturn();

        AnalyticsResponseDTO response = readResponse(result);

        assertThat(response.getMonthlyTrends())
                .anySatisfy(month -> {
                    assertThat(month.getMonth()).isEqualTo("2026-11");
                    assertThat(month.getResolved()).isEqualTo(1);
                });

        assertThat(response.getMonthlyTrends())
                .anySatisfy(month -> {
                    assertThat(month.getMonth()).isEqualTo("2026-10");
                    assertThat(month.getResolved()).isZero();
                });

        // October 1 to November 2 = 32 days = 768 hours.
        assertThat(response.getOverview().getAverageResolutionTimeHours())
                .isEqualTo(768.0);
    }

    @Test
    void getAnalytics_fillsMissingMonthsWithZero()
            throws Exception {

        Complaint complaint = createComplaint(
                "October complaint",
                plumbing,
                firstFloorRoom,
                Status.RESOLVED,
                LocalDateTime.of(2026, 10, 5, 10, 0));

        saveHistory(
                complaint,
                Status.IN_PROGRESS,
                Status.RESOLVED,
                LocalDateTime.of(2026, 12, 5, 10, 0));

        MvcResult result = mockMvc.perform(
                get("/api/admin/analytics")
                        .param("startDate", "2026-10-01")
                        .param("endDate", "2026-12-31")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andReturn();

        AnalyticsResponseDTO response = readResponse(result);

        assertThat(response.getMonthlyTrends()).hasSize(3);

        var trends = response.getMonthlyTrends();

        // October
        assertThat(trends.get(0).getMonth()).isEqualTo("2026-10");
        assertThat(trends.get(0).getCreated()).isEqualTo(1);
        assertThat(trends.get(0).getResolved()).isZero();

        // November: no activity.
        assertThat(trends.get(1).getMonth()).isEqualTo("2026-11");
        assertThat(trends.get(1).getCreated()).isZero();
        assertThat(trends.get(1).getResolved()).isZero();

        // December
        assertThat(trends.get(2).getMonth()).isEqualTo("2026-12");
        assertThat(trends.get(2).getCreated()).isZero();
        assertThat(trends.get(2).getResolved()).isEqualTo(1);
    }

    @Test
    void getAnalytics_returnsZeroResolutionRateWhenAllComplaintsAreRejected()
            throws Exception {

        createComplaint(
                "Rejected complaint",
                plumbing,
                firstFloorRoom,
                Status.REJECTED,
                LocalDateTime.now());

        MvcResult result = mockMvc.perform(
                get("/api/admin/analytics")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andReturn();

        AnalyticsResponseDTO response = readResponse(result);

        assertThat(response.getOverview().getTotalComplaints()).isEqualTo(1);
        assertThat(response.getOverview().getRejected()).isEqualTo(1);
        assertThat(response.getOverview().getResolutionRate()).isEqualTo(0.0);
    }

    // ---------------------------------------------------------
    // Additional helper for creating a location
    // ---------------------------------------------------------

    private Location createLocation(String floor, String roomNumber) {
        Location location = new Location();
        location.setFloor(floor);
        location.setRoomNumber(roomNumber);
        return location;
    }
}


package com.example.demo.hostel.repository;

import com.example.demo.hostel.model.Category;
import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.model.ComplaintHistory;
import com.example.demo.hostel.model.Location;
import com.example.demo.hostel.model.Role;
import com.example.demo.hostel.model.Status;
import com.example.demo.hostel.model.User;
import com.example.demo.hostel.service.SupabaseStorageService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.sql.init.mode=never"
})
class AnalyticsRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private AnalyticsRepository analyticsRepository;

    @Autowired
    private ComplaintRepository complaintRepository;

    @Autowired
    private ComplaintHistoryRepository historyRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private SupabaseStorageService storageService;

    private User student;
    private User admin;

    private Category plumbing;
    private Category electrical;

    private Location firstFloorRoom;
    private Location secondFloorRoom;

    @BeforeEach
    void setUp() {
        student = userRepository.save(user(
                "student@smail.iitm.ac.in", Role.STUDENT));

        admin = userRepository.save(user(
                "admin@smail.iitm.ac.in", Role.ADMIN));

        plumbing = categoryRepository.save(category("Plumbing"));
        electrical = categoryRepository.save(category("Electrical"));

        firstFloorRoom = locationRepository.save(
                location("First", "204", null, false));

        secondFloorRoom = locationRepository.save(
                location("Second", "304", null, false));
    }

    @AfterEach
    void tearDown() {
        // Delete dependent records first.
        jdbcTemplate.update("DELETE FROM upvotes");
        jdbcTemplate.update("DELETE FROM comments");
        jdbcTemplate.update("DELETE FROM work_orders");
        jdbcTemplate.update("DELETE FROM complaint_history");
        jdbcTemplate.update("DELETE FROM complaints");
        jdbcTemplate.update("DELETE FROM categories");
        jdbcTemplate.update("DELETE FROM locations");
        jdbcTemplate.update("DELETE FROM users");
    }

    // ---------------------------------------------------------
    // 1. Status counts
    // ---------------------------------------------------------

    @Test
    void countComplaintsByStatus_returnsAllStatusesAndIncludesArchived() {

        for (Status status : Status.values()) {
            Complaint complaint = createComplaint(
                    plumbing, firstFloorRoom, status);

            if (status == Status.RESOLVED) {
                complaint.setArchived(true);
                complaintRepository.save(complaint);
            }
        }

        Map<String, Long> result = analyticsRepository
                .countComplaintsByStatus(null, null)
                .stream()
                .collect(Collectors.toMap(
                        AnalyticsRepository.StatusCount::getStatus,
                        AnalyticsRepository.StatusCount::getCount));

        assertThat(result)
                .containsEntry("PENDING", 1L)
                .containsEntry("ASSIGNED", 1L)
                .containsEntry("IN_PROGRESS", 1L)
                .containsEntry("RESOLVED", 1L)
                .containsEntry("REJECTED", 1L);
    }

    @Test
    void countComplaintsByStatus_excludesSoftDeletedComplaints() {

        // Create one active complaint.
        createComplaint(plumbing, firstFloorRoom, Status.PENDING);

        Complaint deleted = createComplaint(
                plumbing, firstFloorRoom, Status.PENDING);

        jdbcTemplate.update(
                "UPDATE complaints SET deleted_at = ? WHERE id = ?",
                LocalDateTime.of(2026, 10, 10, 10, 0),
                deleted.getId());

        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM complaints",
                Integer.class);

        Integer activeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM complaints WHERE deleted_at IS NULL",
                Integer.class);

        Map<String, Long> result = analyticsRepository
                .countComplaintsByStatus(null, null)
                .stream()
                .collect(Collectors.toMap(
                        AnalyticsRepository.StatusCount::getStatus,
                        AnalyticsRepository.StatusCount::getCount));

        assertThat(result).containsEntry("PENDING", 1L);
        assertThat(result.get("PENDING")).isEqualTo(1L);
    }

    // ---------------------------------------------------------
    // 2. Category breakdown
    // ---------------------------------------------------------

    @Test
    void countComplaintsByCategory_includesCategoriesWithZeroComplaints() {

        createComplaint(plumbing, firstFloorRoom, Status.PENDING);
        createComplaint(plumbing, firstFloorRoom, Status.RESOLVED);

        Map<String, Long> result = analyticsRepository
                .countComplaintsByCategory(null, null)
                .stream()
                .collect(Collectors.toMap(
                        AnalyticsRepository.CategoryCount::getCategory,
                        AnalyticsRepository.CategoryCount::getCount));

        assertThat(result)
                .containsEntry("Plumbing", 2L)
                .containsEntry("Electrical", 0L);
    }

    @Test
    void countComplaintsByCategory_appliesCreationDateFilter() {

        Complaint octoberComplaint = createComplaint(
                plumbing, firstFloorRoom, Status.PENDING);

        Complaint novemberComplaint = createComplaint(
                plumbing, firstFloorRoom, Status.PENDING);

        setCreatedAt(octoberComplaint,
                LocalDateTime.of(2026, 10, 15, 10, 0));

        setCreatedAt(novemberComplaint,
                LocalDateTime.of(2026, 11, 5, 10, 0));

        Map<String, Long> result = analyticsRepository
                .countComplaintsByCategory(
                        LocalDate.of(2026, 10, 1),
                        LocalDate.of(2026, 10, 31))
                .stream()
                .collect(Collectors.toMap(
                        AnalyticsRepository.CategoryCount::getCategory,
                        AnalyticsRepository.CategoryCount::getCount));

        assertThat(result)
                .containsEntry("Plumbing", 1L)
                .containsEntry("Electrical", 0L);
    }

    // ---------------------------------------------------------
    // 3. Floor breakdown
    // ---------------------------------------------------------

    @Test
    void countComplaintsByFloor_includesFloorsWithZeroComplaints() {

        createComplaint(plumbing, firstFloorRoom, Status.PENDING);
        createComplaint(plumbing, firstFloorRoom, Status.RESOLVED);

        Map<String, Long> result = analyticsRepository
                .countComplaintsByFloor(null, null)
                .stream()
                .collect(Collectors.toMap(
                        AnalyticsRepository.FloorCount::getFloor,
                        AnalyticsRepository.FloorCount::getCount));

        assertThat(result)
                .containsEntry("First", 2L)
                .containsEntry("Second", 0L);
    }

    // ---------------------------------------------------------
    // 4. Recurring issues
    // ---------------------------------------------------------

    @Test
    void findRecurringIssues_groupsRoomIssuesByFloorRoomAndCategory() {

        createComplaint(plumbing, firstFloorRoom, Status.PENDING);
        createComplaint(plumbing, firstFloorRoom, Status.RESOLVED);

        createComplaint(electrical, firstFloorRoom, Status.PENDING);

        var result = analyticsRepository.findRecurringIssues(null, null);

        assertThat(result)
                .anySatisfy(issue -> {
                    assertThat(issue.getType()).isEqualTo("ROOM");
                    assertThat(issue.getFloor()).isEqualTo("First");
                    assertThat(issue.getRoomNumber()).isEqualTo("204");
                    assertThat(issue.getCategory()).isEqualTo("Plumbing");
                    assertThat(issue.getCount()).isEqualTo(2L);
                });

        assertThat(result)
                .noneSatisfy(issue -> assertThat(issue.getCategory())
                        .isEqualTo("Electrical"));
    }

    @Test
    void findRecurringIssues_groupsCommunityIssuesByWing() {

        Location wingA = locationRepository.save(
                location("First", null, "A", true));

        Location unspecifiedWing = locationRepository.save(
                location("Second", null, null, true));

        createComplaint(plumbing, wingA, Status.PENDING);
        createComplaint(plumbing, wingA, Status.RESOLVED);

        createComplaint(electrical, unspecifiedWing, Status.PENDING);
        createComplaint(electrical, unspecifiedWing, Status.RESOLVED);

        var result = analyticsRepository.findRecurringIssues(null, null);

        assertThat(result)
                .anySatisfy(issue -> {
                    assertThat(issue.getType()).isEqualTo("COMMUNITY_AREA");
                    assertThat(issue.getFloor()).isEqualTo("First");
                    assertThat(issue.getWing()).isEqualTo("A");
                    assertThat(issue.getCategory()).isEqualTo("Plumbing");
                    assertThat(issue.getCount()).isEqualTo(2L);
                });

        assertThat(result)
                .anySatisfy(issue -> {
                    assertThat(issue.getType()).isEqualTo("COMMUNITY_AREA");
                    assertThat(issue.getFloor()).isEqualTo("Second");
                    assertThat(issue.getWing()).isEqualTo("UNSPECIFIED");
                    assertThat(issue.getCategory()).isEqualTo("Electrical");
                    assertThat(issue.getCount()).isEqualTo(2L);
                });
    }

    @Test
    void findRecurringIssues_excludesGroupsWithOnlyOneComplaint() {

        createComplaint(plumbing, firstFloorRoom, Status.PENDING);

        var result = analyticsRepository.findRecurringIssues(null, null);

        assertThat(result).isEmpty();
    }

    // ---------------------------------------------------------
    // 5. Monthly created complaints
    // ---------------------------------------------------------

    @Test
    void countComplaintsCreatedByMonth_groupsComplaintsByMonth() {

        Complaint september = createComplaint(
                plumbing, firstFloorRoom, Status.PENDING);

        Complaint october = createComplaint(
                plumbing, firstFloorRoom, Status.PENDING);

        Complaint october2 = createComplaint(
                plumbing, firstFloorRoom, Status.PENDING);

        setCreatedAt(september,
                LocalDateTime.of(2026, 9, 10, 10, 0));

        setCreatedAt(october,
                LocalDateTime.of(2026, 10, 5, 10, 0));

        setCreatedAt(october2,
                LocalDateTime.of(2026, 10, 20, 10, 0));

        var result = analyticsRepository
                .countComplaintsCreatedByMonth(null, null);

        Map<String, Long> counts = result.stream()
                .collect(Collectors.toMap(
                        AnalyticsRepository.MonthlyCount::getMonth,
                        AnalyticsRepository.MonthlyCount::getCount));

        assertThat(counts)
                .containsEntry("2026-09", 1L)
                .containsEntry("2026-10", 2L);
    }

    @Test
    void countComplaintsCreatedByMonth_appliesCreationDateFilter() {

        Complaint september = createComplaint(
                plumbing, firstFloorRoom, Status.PENDING);

        Complaint october = createComplaint(
                plumbing, firstFloorRoom, Status.PENDING);

        setCreatedAt(september,
                LocalDateTime.of(2026, 9, 10, 10, 0));

        setCreatedAt(october,
                LocalDateTime.of(2026, 10, 10, 10, 0));

        var result = analyticsRepository.countComplaintsCreatedByMonth(
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMonth()).isEqualTo("2026-10");
        assertThat(result.get(0).getCount()).isEqualTo(1L);
    }

    // ---------------------------------------------------------
    // 6. Monthly resolved complaints
    // ---------------------------------------------------------

    @Test
    void countComplaintsResolvedByMonth_usesLatestResolutionTransition() {

        Complaint complaint = createComplaint(
                plumbing, firstFloorRoom, Status.IN_PROGRESS);

        // First resolution in September.
        saveHistory(complaint, Status.IN_PROGRESS, Status.RESOLVED,
                LocalDateTime.of(2026, 9, 10, 10, 0));

        // Reopened in October.
        saveHistory(complaint, Status.RESOLVED, Status.IN_PROGRESS,
                LocalDateTime.of(2026, 10, 1, 10, 0));

        // Resolved again in November.
        saveHistory(complaint, Status.IN_PROGRESS, Status.RESOLVED,
                LocalDateTime.of(2026, 11, 5, 10, 0));

        var result = analyticsRepository
                .countComplaintsResolvedByMonth(null, null);

        assertThat(result)
                .anySatisfy(month -> {
                    assertThat(month.getMonth()).isEqualTo("2026-11");
                    assertThat(month.getCount()).isEqualTo(1L);
                });

        assertThat(result)
                .noneSatisfy(month -> assertThat(month.getMonth()).isEqualTo("2026-09"));
    }

    @Test
    void countComplaintsResolvedByMonth_includesSubsequentlyReopenedComplaints() {

        Complaint complaint = createComplaint(
                plumbing, firstFloorRoom, Status.IN_PROGRESS);

        saveHistory(complaint, Status.IN_PROGRESS, Status.RESOLVED,
                LocalDateTime.of(2026, 10, 15, 10, 0));

        // Current status is IN_PROGRESS, but the resolution event still counts.
        var result = analyticsRepository.countComplaintsResolvedByMonth(
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMonth()).isEqualTo("2026-10");
        assertThat(result.get(0).getCount()).isEqualTo(1L);
    }

    // ---------------------------------------------------------
    // 7. Average resolution time
    // ---------------------------------------------------------

    @Test
    void findAverageResolutionTimeHours_usesLatestResolutionAndOnlyCurrentlyResolved() {

        Complaint resolved = createComplaint(
                plumbing, firstFloorRoom, Status.RESOLVED);

        Complaint reopened = createComplaint(
                plumbing, firstFloorRoom, Status.IN_PROGRESS);

        // Created at: January 1, 2026, 10 AM.
        setCreatedAt(resolved,
                LocalDateTime.of(2026, 1, 1, 10, 0));

        // First resolution after 12 hours.
        saveHistory(resolved, Status.IN_PROGRESS, Status.RESOLVED,
                LocalDateTime.of(2026, 1, 1, 22, 0));

        // Latest resolution after 48 hours.
        saveHistory(resolved, Status.IN_PROGRESS, Status.RESOLVED,
                LocalDateTime.of(2026, 1, 3, 10, 0));

        // This complaint was resolved but subsequently reopened.
        saveHistory(reopened, Status.IN_PROGRESS, Status.RESOLVED,
                LocalDateTime.of(2026, 1, 2, 10, 0));

        Double result = analyticsRepository.findAverageResolutionTimeHours(
                null, null);

        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(48.0);
    }

    @Test
    void findAverageResolutionTimeHours_returnsNullWhenNoResolvedComplaintsExist() {

        createComplaint(plumbing, firstFloorRoom, Status.PENDING);

        Double result = analyticsRepository.findAverageResolutionTimeHours(
                null, null);

        assertThat(result).isNull();
    }

    // ---------------------------------------------------------
    // 8-9. Staff members and their work orders
    // ---------------------------------------------------------

    @Test
    void findStaffMembers_returnsOnlyStaff_withProfileNameFirst() {

        User staff = user("staff@example.com", Role.STAFF);
        staff.setFullName("Ravi Kumar");
        userRepository.save(staff);

        assertThat(analyticsRepository.findStaffMembers())
                .extracting(AnalyticsRepository.StaffMember::getName,
                        AnalyticsRepository.StaffMember::getRole)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Ravi Kumar", "STAFF"));
    }

    @Test
    void findStaffWorkOrders_returnsActiveAlways_andFinishedOnlyInRange() {

        Complaint complaint = createComplaint(plumbing, firstFloorRoom, Status.ASSIGNED);
        LocalDateTime deadline = LocalDateTime.of(2026, 10, 20, 18, 0);

        // Active, created long before the range
        insertWorkOrder(complaint, "OPEN",
                LocalDateTime.of(2026, 1, 5, 10, 0), null, deadline);
        // Done inside the range
        insertWorkOrder(complaint, "DONE",
                LocalDateTime.of(2026, 10, 9, 10, 0), LocalDateTime.of(2026, 10, 10, 10, 0), deadline);
        // Done before the range
        insertWorkOrder(complaint, "DONE",
                LocalDateTime.of(2026, 9, 1, 10, 0), LocalDateTime.of(2026, 9, 2, 10, 0), null);
        // Cancelled inside the range
        insertWorkOrder(complaint, "CANCELLED",
                LocalDateTime.of(2026, 10, 11, 10, 0), LocalDateTime.of(2026, 10, 12, 10, 0), null);

        var result = analyticsRepository.findStaffWorkOrders(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        assertThat(result)
                .extracting(AnalyticsRepository.StaffWorkOrder::getStatus,
                        AnalyticsRepository.StaffWorkOrder::getCreatedInRange,
                        AnalyticsRepository.StaffWorkOrder::getFinishedInRange)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("OPEN", false, false),
                        org.assertj.core.groups.Tuple.tuple("DONE", true, true),
                        org.assertj.core.groups.Tuple.tuple("CANCELLED", true, true));

        var done = result.stream().filter(row -> "DONE".equals(row.getStatus())).findFirst().orElseThrow();
        assertThat(done.getUserId()).isEqualTo(admin.getId());
        assertThat(done.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 10, 9, 10, 0));
        assertThat(done.getFinishedAt()).isEqualTo(LocalDateTime.of(2026, 10, 10, 10, 0));
        assertThat(done.getDeadline()).isEqualTo(deadline);

        var open = result.stream().filter(row -> "OPEN".equals(row.getStatus())).findFirst().orElseThrow();
        assertThat(open.getFinishedAt()).isNull();
    }

    @Test
    void findStaffWorkOrders_excludesSoftDeletedComplaints() {

        Complaint deleted = createComplaint(plumbing, firstFloorRoom, Status.PENDING);
        insertWorkOrder(deleted, "OPEN", LocalDateTime.of(2026, 10, 9, 10, 0), null, null);

        jdbcTemplate.update(
                "UPDATE complaints SET deleted_at = ? WHERE id = ?",
                LocalDateTime.of(2026, 10, 10, 10, 0),
                deleted.getId());

        assertThat(analyticsRepository.findStaffWorkOrders(null, null)).isEmpty();
    }

    // ---------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------

    // Assigned to the admin; updatedAt stands for when a finished one finished
    private void insertWorkOrder(
            Complaint complaint,
            String status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime deadline) {

        jdbcTemplate.update("""
                INSERT INTO work_orders
                    (work_order_id, complaint_id, assignee_id, priority, status, deadline, created_at, updated_at)
                VALUES (gen_random_uuid(), ?, ?, 'MEDIUM', ?, ?, ?, ?)
                """,
                complaint.getId(), admin.getId(), status, deadline, createdAt, updatedAt);
    }

    private Complaint createComplaint(
            Category category,
            Location location,
            Status status) {

        Complaint complaint = new Complaint();
        complaint.setTitle("Test complaint");
        complaint.setDescription("Test description");
        complaint.setCategory(category);
        complaint.setLocation(location);
        complaint.setStudent(student);
        complaint.setStatus(status);

        return complaintRepository.saveAndFlush(complaint);
    }

    private void setCreatedAt(
            Complaint complaint,
            LocalDateTime createdAt) {

        jdbcTemplate.update(
                "UPDATE complaints SET created_at = ? WHERE id = ?",
                createdAt,
                complaint.getId());
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

        ComplaintHistory saved = historyRepository.saveAndFlush(history);

        jdbcTemplate.update(
                "UPDATE complaint_history SET changed_at = ? WHERE history_id = ?",
                changedAt,
                saved.getId());
    }

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
        return category;
    }

    private Location location(
            String floor,
            String roomNumber,
            String wing,
            boolean communityArea) {

        Location location = new Location();
        location.setFloor(floor);
        location.setRoomNumber(roomNumber);
        location.setWing(wing);
        location.setCommunityArea(communityArea);

        return location;
    }
}

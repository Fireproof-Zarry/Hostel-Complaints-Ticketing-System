package com.example.demo.hostel.service;

import com.example.demo.hostel.model.Category;
import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.model.Location;
import com.example.demo.hostel.model.Role;
import com.example.demo.hostel.model.Status;
import com.example.demo.hostel.model.User;
import com.example.demo.hostel.repository.CategoryRepository;
import com.example.demo.hostel.repository.ComplaintHistoryRepository;
import com.example.demo.hostel.repository.ComplaintRepository;
import com.example.demo.hostel.repository.LocationRepository;
import com.example.demo.hostel.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:complaint-archival-test",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class ComplaintArchivalServiceIntegrationTest {

    private static final String STUDENT_EMAIL = "student@smail.iitm.ac.in";

    @Autowired
    private ComplaintArchivalService archivalService;

    @Autowired
    private AdminService adminService;

    @Autowired
    private ComplaintService complaintService;

    @Autowired
    private ComplaintTransitionService transitionService;

    @Autowired
    private ComplaintRepository complaintRepository;

    @Autowired
    private ComplaintHistoryRepository complaintHistoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User student;
    private String adminId;
    private Category category;
    private Location location;

    private Long oldResolved;
    private Long oldRejected;
    private Long recentResolved;
    private Long oldPending;
    private Long oldInProgress;

    @BeforeEach
    void setUp() {
        student = userRepository.save(user(STUDENT_EMAIL, Role.STUDENT));
        adminId = userRepository.save(user("admin@smail.iitm.ac.in", Role.ADMIN)).getId();

        category = new Category();
        category.setName("Plumbing");
        category.setDefaultSlaHours(48);
        category = categoryRepository.save(category);

        location = new Location();
        location.setFloor("First");
        location.setRoomNumber("204");
        location = locationRepository.save(location);

        oldResolved = complaint("Old resolved", Status.RESOLVED, 45);
        oldRejected = complaint("Old rejected", Status.REJECTED, 31);
        recentResolved = complaint("Recent resolved", Status.RESOLVED, 29);
        oldPending = complaint("Old pending", Status.PENDING, 60);
        oldInProgress = complaint("Old in progress", Status.IN_PROGRESS, 60);
    }

    @AfterEach
    void tearDown() {
        complaintHistoryRepository.deleteAll();
        complaintRepository.deleteAll();
        categoryRepository.deleteAll();
        locationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void archivesOnlyResolvedOrRejectedComplaintsOlderThan30Days() {
        int archived = archivalService.archiveOldComplaints();

        assertThat(archived).isEqualTo(2);
        assertThat(isArchived(oldResolved)).isTrue();
        assertThat(isArchived(oldRejected)).isTrue();
        assertThat(isArchived(recentResolved)).isFalse();
        assertThat(isArchived(oldPending)).isFalse();
        assertThat(isArchived(oldInProgress)).isFalse();
    }

    @Test
    void onlyTheOldResolvedComplaintIsArchivedNotThePendingOne() {
        // Exactly the two-complaint scenario: an old RESOLVED and an old PENDING complaint
        complaintRepository.deleteAllById(List.of(oldRejected, recentResolved, oldInProgress));

        int archived = archivalService.archiveOldComplaints();

        assertThat(archived).isEqualTo(1);
        assertThat(isArchived(oldResolved)).isTrue();
        assertThat(isArchived(oldPending)).isFalse();
        assertThat(ids(complaintRepository.findByIsArchivedTrue())).containsExactly(oldResolved);
    }

    @Test
    void archivingKeepsTheOriginalUpdatedAt() {
        LocalDateTime before = updatedAt(oldResolved);

        archivalService.archiveOldComplaints();

        assertThat(updatedAt(oldResolved)).isEqualTo(before);
    }

    @Test
    void secondRunArchivesNothingNew() {
        archivalService.archiveOldComplaints();

        assertThat(archivalService.archiveOldComplaints()).isZero();
    }

    @Test
    void adminDashboardHidesArchivedAndArchiveEndpointListsThem() {
        archivalService.archiveOldComplaints();

        List<Long> dashboard = ids(adminService.getComplaints(null, null, null, null, null, null));
        assertThat(dashboard).containsExactlyInAnyOrder(recentResolved, oldPending, oldInProgress);

        // Status filters also skip archived complaints
        assertThat(ids(adminService.getComplaints(Status.RESOLVED, null, null, null, null, null)))
                .containsExactly(recentResolved);

        // Archive view: most recently updated first
        assertThat(ids(adminService.getArchivedComplaints())).containsExactly(oldRejected, oldResolved);
    }

    @Test
    void studentsStillSeeTheirArchivedComplaints() {
        archivalService.archiveOldComplaints();

        assertThat(ids(complaintService.getMyComplaints(STUDENT_EMAIL)))
                .containsExactlyInAnyOrder(oldResolved, oldRejected, recentResolved, oldPending, oldInProgress);
    }

    @Test
    void reopeningAnArchivedComplaintUnarchivesIt() {
        archivalService.archiveOldComplaints();

        transitionService.changeComplaintStatus(oldResolved, adminId, "IN_PROGRESS", "Leak is back");

        assertThat(isArchived(oldResolved)).isFalse();
        assertThat(ids(adminService.getComplaints(null, null, null, null, null, null))).contains(oldResolved);
    }

    @Test
    void jobRunsDailyAt2amIndiaTime() throws Exception {
        Scheduled scheduled = ComplaintArchivalService.class
                .getMethod("archiveOldComplaints")
                .getAnnotation(Scheduled.class);

        assertThat(scheduled.cron()).isEqualTo("0 0 2 * * ?");
        assertThat(scheduled.zone()).isEqualTo("Asia/Kolkata");
    }

    private Long complaint(String title, Status status, int daysSinceUpdate) {
        Complaint complaint = new Complaint();
        complaint.setTitle(title);
        complaint.setDescription("Archival test");
        complaint.setCategory(category);
        complaint.setLocation(location);
        complaint.setStudent(student);
        complaint.setStatus(status);
        Long id = complaintRepository.save(complaint).getId();

        // @UpdateTimestamp always writes "now", so age the row directly
        LocalDateTime updatedAt = LocalDateTime.now().minusDays(daysSinceUpdate).truncatedTo(ChronoUnit.SECONDS);
        jdbcTemplate.update("UPDATE complaints SET updated_at = ? WHERE id = ?", Timestamp.valueOf(updatedAt), id);
        return id;
    }

    private boolean isArchived(Long id) {
        return complaintRepository.findById(id).orElseThrow().isArchived();
    }

    private LocalDateTime updatedAt(Long id) {
        return jdbcTemplate.queryForObject(
                "SELECT updated_at FROM complaints WHERE id = ?", Timestamp.class, id).toLocalDateTime();
    }

    private static List<Long> ids(List<Complaint> complaints) {
        return complaints.stream().map(Complaint::getId).toList();
    }

    private static User user(String email, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setName(email);
        user.setRole(role);
        return user;
    }
}

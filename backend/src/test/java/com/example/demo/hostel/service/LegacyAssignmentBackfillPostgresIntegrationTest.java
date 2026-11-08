package com.example.demo.hostel.service;

import com.example.demo.hostel.model.Category;
import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.model.Location;
import com.example.demo.hostel.model.Role;
import com.example.demo.hostel.model.Status;
import com.example.demo.hostel.model.User;
import com.example.demo.hostel.model.WorkOrder;
import com.example.demo.hostel.model.WorkOrderPriority;
import com.example.demo.hostel.model.WorkOrderStatus;
import com.example.demo.hostel.repository.CategoryRepository;
import com.example.demo.hostel.repository.ComplaintRepository;
import com.example.demo.hostel.repository.LocationRepository;
import com.example.demo.hostel.repository.UserRepository;
import com.example.demo.hostel.repository.WorkOrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs schema_nov08.sql against real PostgreSQL. Skipped when Docker is
 * unavailable; runs in CI.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.sql.init.mode=never"
})
class LegacyAssignmentBackfillPostgresIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private ComplaintRepository complaintRepository;
    @Autowired private WorkOrderRepository workOrderRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;

    private User student;
    private User warden;
    private Category category;
    private Location location;

    @BeforeEach
    void setUp() {
        student = userRepository.save(user("student@smail.iitm.ac.in", Role.STUDENT));
        warden = userRepository.save(user("warden@gmail.com", Role.STAFF));
        Category newCategory = new Category();
        newCategory.setName("Electrical");
        newCategory.setDefaultSlaHours(48);
        category = categoryRepository.save(newCategory);
        Location newLocation = new Location();
        newLocation.setFloor("First");
        newLocation.setRoomNumber("204");
        location = locationRepository.save(newLocation);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM work_orders");
        jdbcTemplate.update("DELETE FROM complaint_history");
        jdbcTemplate.update("DELETE FROM complaints");
        categoryRepository.deleteAll();
        locationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void oldAssignmentsGetWorkOrdersAndPendingOnesBecomeAssigned() {
        Long pending = complaint(Status.PENDING, "WARDEN@gmail.com");
        Long inProgress = complaint(Status.IN_PROGRESS, "warden@gmail.com");

        migrate();
        migrate();

        assertThat(status(pending)).isEqualTo("ASSIGNED");
        assertThat(activeWorkOrderStatus(pending)).isEqualTo("OPEN");
        assertThat(activeWorkOrderStatus(inProgress)).isEqualTo("IN_PROGRESS");
        assertThat(historyRows(pending)).isEqualTo(1);
        assertThat(historyRows(inProgress)).isZero();
        assertThat(workOrderRepository.count()).isEqualTo(2);
    }

    @Test
    void leavesEverythingElseAlone() {
        Long alreadyHasOne = complaint(Status.ASSIGNED, "warden@gmail.com");
        WorkOrder existing = new WorkOrder();
        existing.setComplaint(complaintRepository.findById(alreadyHasOne).orElseThrow());
        existing.setAssignee(warden);
        existing.setPriority(WorkOrderPriority.URGENT);
        existing.setStatus(WorkOrderStatus.OPEN);
        workOrderRepository.save(existing);
        Long notStaff = complaint(Status.PENDING, "student@smail.iitm.ac.in");
        Long resolved = complaint(Status.RESOLVED, "warden@gmail.com");
        Long deleted = complaint(Status.PENDING, "warden@gmail.com");
        jdbcTemplate.update("UPDATE complaints SET deleted_at = now() WHERE id = ?", deleted);
        Long unassigned = complaint(Status.PENDING, null);

        migrate();

        assertThat(workOrderRepository.count()).isEqualTo(1);
        assertThat(status(notStaff)).isEqualTo("PENDING");
        assertThat(status(resolved)).isEqualTo("RESOLVED");
        assertThat(status(deleted)).isEqualTo("PENDING");
        assertThat(status(unassigned)).isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM complaint_history", Long.class)).isZero();
    }

    private void migrate() {
        new ResourceDatabasePopulator(new ClassPathResource("schema_nov08.sql")).execute(dataSource);
    }

    private Long complaint(Status status, String assignedTo) {
        Complaint complaint = new Complaint();
        complaint.setTitle("Fan not working");
        complaint.setDescription("Backfill test");
        complaint.setCategory(category);
        complaint.setLocation(location);
        complaint.setStudent(student);
        complaint.setStatus(status);
        complaint.setAssignedTo(assignedTo);
        return complaintRepository.save(complaint).getId();
    }

    private String status(Long complaintId) {
        return jdbcTemplate.queryForObject("SELECT status FROM complaints WHERE id = ?", String.class, complaintId);
    }

    private String activeWorkOrderStatus(Long complaintId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM work_orders WHERE complaint_id = ? AND status IN ('OPEN', 'IN_PROGRESS')",
                String.class, complaintId);
    }

    private long historyRows(Long complaintId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM complaint_history WHERE complaint_id = ?", Long.class, complaintId);
    }

    private static User user(String email, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setName(email);
        user.setRole(role);
        return user;
    }
}

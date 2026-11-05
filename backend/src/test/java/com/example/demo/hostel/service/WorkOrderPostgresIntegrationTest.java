package com.example.demo.hostel.service;

import com.example.demo.hostel.dto.UpdateWorkOrderRequest;
import com.example.demo.hostel.dto.WorkOrderView;
import com.example.demo.hostel.model.Category;
import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.model.Location;
import com.example.demo.hostel.model.Role;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs schema_nov03.sql and the work-order queries against real PostgreSQL:
 * the one-active-work-order index is a partial index, which H2 does not have.
 * Skipped when Docker is unavailable; runs in CI.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.sql.init.mode=never"
})
class WorkOrderPostgresIntegrationTest {

    private static final String WARDEN = "warden@gmail.com";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private WorkOrderService workOrderService;
    @Autowired private WorkOrderRepository workOrderRepository;
    @Autowired private ComplaintRepository complaintRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;

    private User warden;
    private User student;
    private Category category;
    private Location location;

    @BeforeEach
    void setUp() {
        new ResourceDatabasePopulator(new ClassPathResource("schema_nov03.sql")).execute(dataSource);

        warden = userRepository.save(user(WARDEN, Role.STAFF));
        student = userRepository.save(user("student@smail.iitm.ac.in", Role.STUDENT));

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
    void theDatabaseRefusesASecondActiveWorkOrder() {
        Long complaintId = complaint("Fan not working");
        workOrderService.assignInCharge(complaintId, null, WorkOrderPriority.HIGH, null, WARDEN);

        WorkOrder second = new WorkOrder();
        second.setComplaint(complaintRepository.findById(complaintId).orElseThrow());
        second.setAssignee(warden);
        second.setPriority(WorkOrderPriority.LOW);

        assertThatThrownBy(() -> workOrderRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aComplaintCanBeAssignedAgainAfterItsWorkOrderIsCancelled() {
        Long complaintId = complaint("Fan not working");
        WorkOrderView first = workOrderService.assignInCharge(complaintId, null, WorkOrderPriority.HIGH, null, WARDEN);
        workOrderService.updateWorkOrder(first.id(),
                new UpdateWorkOrderRequest(null, null, null, WorkOrderStatus.CANCELLED), WARDEN);

        workOrderService.assignInCharge(complaintId, null, WorkOrderPriority.LOW, null, WARDEN);

        assertThat(workOrderRepository.count()).isEqualTo(2);
    }

    @Test
    void theQueueSortsByDeadlineThenUrgencyAndSkipsDeletedComplaints() {
        Long noDeadline = complaint("No deadline");
        Long low = complaint("Low priority");
        Long urgent = complaint("Urgent");
        Long deleted = complaint("Deleted");
        LocalDateTime deadline = LocalDateTime.now().plusDays(3);
        workOrderService.assignInCharge(noDeadline, null, WorkOrderPriority.URGENT, null, WARDEN);
        workOrderService.assignInCharge(low, null, WorkOrderPriority.LOW, deadline, WARDEN);
        workOrderService.assignInCharge(urgent, null, WorkOrderPriority.URGENT, deadline, WARDEN);
        workOrderService.assignInCharge(deleted, null, WorkOrderPriority.URGENT, deadline, WARDEN);
        jdbcTemplate.update("UPDATE complaints SET deleted_at = now() WHERE id = ?", deleted);

        assertThat(workOrderService.getMyQueue(WARDEN))
                .extracting(WorkOrderView::complaintId)
                .containsExactly(urgent, low, noDeadline);
    }

    private Long complaint(String title) {
        Complaint complaint = new Complaint();
        complaint.setTitle(title);
        complaint.setDescription("Postgres test");
        complaint.setCategory(category);
        complaint.setLocation(location);
        complaint.setStudent(student);
        return complaintRepository.save(complaint).getId();
    }

    private static User user(String email, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setName(email);
        user.setRole(role);
        return user;
    }
}

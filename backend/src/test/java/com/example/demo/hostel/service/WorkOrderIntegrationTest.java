
package com.example.demo.hostel.service;

import com.example.demo.hostel.model.Category;
import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.model.ComplaintHistory;
import com.example.demo.hostel.model.Location;
import com.example.demo.hostel.model.Role;
import com.example.demo.hostel.model.Status;
import com.example.demo.hostel.model.User;
import com.example.demo.hostel.model.WorkOrder;

import com.example.demo.hostel.repository.CategoryRepository;
import com.example.demo.hostel.repository.ComplaintHistoryRepository;
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
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.server.ResponseStatusException;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@Testcontainers
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.sql.init.mode=never"
})
class WorkOrderIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private WorkOrderService workOrderService;

    @Autowired
    private WorkOrderRepository workOrderRepository;

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

    // Prevent external storage and notification calls.
    @MockitoBean
    private SupabaseStorageService storageService;

    @MockitoBean
    private FcmNotificationService fcmNotificationService;

    @MockitoSpyBean
    private WorkOrderRepository workOrderRepositorySpy;

    private Complaint complaint;
    private User student;
    private User admin;

    @BeforeEach
    void setUp() {
        student = userRepository.save(
                user("student@smail.iitm.ac.in", Role.STUDENT));

        admin = userRepository.save(
                user("admin@smail.iitm.ac.in", Role.ADMIN));

        Category category = new Category();
        category.setName("Electrical");
        category.setDefaultSlaHours(48);

        Location location = new Location();
        location.setFloor("Second");
        location.setRoomNumber("301");

        complaint = new Complaint();
        complaint.setTitle("Broken fan");
        complaint.setDescription("Not spinning");
        complaint.setCategory(categoryRepository.save(category));
        complaint.setLocation(locationRepository.save(location));
        complaint.setStudent(student);

        complaint = complaintRepository.save(complaint);
    }

    @AfterEach
    void tearDown() {
        workOrderRepository.deleteAll();
        complaintHistoryRepository.deleteAll();
        complaintRepository.deleteAll();
        categoryRepository.deleteAll();
        locationRepository.deleteAll();
        userRepository.deleteAll();
    }

    // --------------------------------------------------
    // ASSIGN WORK ORDER
    // --------------------------------------------------

    @Test
    void assignWorkOrder_persistsWorkOrderAndUpdatesComplaint() {
        LocalDateTime deadline = LocalDateTime.now().plusDays(2);

        WorkOrder result = workOrderService.assignWorkOrder(
                complaint.getId(),
                admin.getId(),
                "HIGH",
                deadline);

        assertThat(result.getId()).isNotNull();
        assertThat(result.getPriority()).isEqualTo("HIGH");
        assertThat(result.getStatus())
                .isEqualTo(WorkOrder.STATUS_ASSIGNED);
        assertThat(result.getDeadline()).isEqualTo(deadline);

        WorkOrder saved = workOrderRepository
                .findById(result.getId()).orElseThrow();

        assertThat(saved.getComplaint().getId())
                .isEqualTo(complaint.getId());
        assertThat(saved.getAssignee().getId())
                .isEqualTo(admin.getId());

        Complaint updatedComplaint = complaintRepository
                .findById(complaint.getId()).orElseThrow();

        assertThat(updatedComplaint.getStatus())
                .isEqualTo(Status.ASSIGNED);
        assertThat(updatedComplaint.getAssignedTo())
                .isEqualTo(admin.getEmail());

        assertThat(complaintHistoryRepository.findAll())
                .hasSize(1)
                .first()
                .satisfies(history -> {
                    assertThat(history.getOldStatus())
                            .isEqualTo(Status.PENDING);
                    assertThat(history.getNewStatus())
                            .isEqualTo(Status.ASSIGNED);
                    assertThat(history.getReason())
                            .isEqualTo("Work order assigned");
                });
    }

    @Test
    void assignWorkOrder_rejectsDuplicateAssignment() {
        workOrderService.assignWorkOrder(
                complaint.getId(), admin.getId(), "HIGH", null);

        assertThatThrownBy(() -> workOrderService.assignWorkOrder(
                complaint.getId(), admin.getId(), "LOW", null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception)
                        .getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));

        assertThat(workOrderRepository.count()).isEqualTo(1);
        assertThat(complaintHistoryRepository.count()).isEqualTo(1);
    }

    @Test
    void assignWorkOrder_rejectsInvalidPriority() {
        assertThatThrownBy(() -> workOrderService.assignWorkOrder(
                complaint.getId(), admin.getId(), "CRITICAL", null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception)
                        .getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        assertThat(workOrderRepository.count()).isZero();
        assertThat(complaintHistoryRepository.count()).isZero();
    }

    @Test
    void assignWorkOrder_rollsBackWhenWorkOrderSaveFails() {
        doThrow(new IllegalStateException("simulated failure"))
                .when(workOrderRepositorySpy).save(any(WorkOrder.class));

        assertThatThrownBy(() -> workOrderService.assignWorkOrder(
                complaint.getId(), admin.getId(), "HIGH", null))
                .hasMessageContaining("simulated failure");

        Complaint unchanged = complaintRepository
                .findById(complaint.getId()).orElseThrow();

        assertThat(unchanged.getStatus()).isEqualTo(Status.PENDING);
        assertThat(unchanged.getAssignedTo()).isNull();

        assertThat(workOrderRepository.count()).isZero();
        assertThat(complaintHistoryRepository.count()).isZero();
    }

    // --------------------------------------------------
    // RETRIEVE WORK ORDERS
    // --------------------------------------------------

    @Test
    void getAllWorkOrders_returnsPersistedWorkOrders() {
        workOrderService.assignWorkOrder(
                complaint.getId(), admin.getId(), "MEDIUM", null);

        List<WorkOrder> result = workOrderService.getAllWorkOrders();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPriority()).isEqualTo("MEDIUM");
    }

    @Test
    void getWorkOrder_returnsPersistedWorkOrder() {
        WorkOrder created = workOrderService.assignWorkOrder(
                complaint.getId(), admin.getId(), "HIGH", null);

        WorkOrder result = workOrderService.getWorkOrder(created.getId());

        assertThat(result.getId()).isEqualTo(created.getId());
        assertThat(result.getComplaint().getId())
                .isEqualTo(complaint.getId());
    }

    @Test
    void getWorkOrdersByAssignee_returnsMatchingWorkOrders() {
        workOrderService.assignWorkOrder(
                complaint.getId(), admin.getId(), "HIGH", null);

        List<WorkOrder> result = workOrderService.getWorkOrdersByAssignee(admin.getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAssignee().getId())
                .isEqualTo(admin.getId());
    }

    // --------------------------------------------------
    // UPDATE WORK ORDER STATUS
    // --------------------------------------------------

    @Test
    void updateWorkOrderStatus_assignedToInProgress_updatesComplaint() {
        WorkOrder created = workOrderService.assignWorkOrder(
                complaint.getId(), admin.getId(), "HIGH", null);

        workOrderService.updateWorkOrderStatus(
                created.getId(), "IN_PROGRESS", null);

        WorkOrder updatedWorkOrder = workOrderRepository
                .findById(created.getId()).orElseThrow();

        Complaint updatedComplaint = complaintRepository
                .findById(complaint.getId()).orElseThrow();

        assertThat(updatedWorkOrder.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(updatedComplaint.getStatus())
                .isEqualTo(Status.IN_PROGRESS);

        assertThat(complaintHistoryRepository.findAll())
                .hasSize(2)
                .extracting(ComplaintHistory::getNewStatus)
                .containsExactly(Status.ASSIGNED, Status.IN_PROGRESS);
    }

    @Test
    void updateWorkOrderStatus_inProgressToCompleted_resolvesComplaint() {
        WorkOrder created = workOrderService.assignWorkOrder(
                complaint.getId(), admin.getId(), "HIGH", null);

        workOrderService.updateWorkOrderStatus(
                created.getId(), "IN_PROGRESS", null);

        workOrderService.updateWorkOrderStatus(
                created.getId(), "COMPLETED", "Fan replaced");

        WorkOrder completed = workOrderRepository
                .findById(created.getId()).orElseThrow();

        Complaint resolvedComplaint = complaintRepository
                .findById(complaint.getId()).orElseThrow();

        assertThat(completed.getStatus()).isEqualTo("COMPLETED");
        assertThat(completed.getCompletionNotes())
                .isEqualTo("Fan replaced");

        assertThat(resolvedComplaint.getStatus())
                .isEqualTo(Status.RESOLVED);

        assertThat(complaintHistoryRepository.findAll())
                .hasSize(3)
                .extracting(ComplaintHistory::getNewStatus)
                .containsExactly(
                        Status.ASSIGNED,
                        Status.IN_PROGRESS,
                        Status.RESOLVED);
    }

    @Test
    void updateWorkOrderStatus_rejectsInvalidTransition() {
        WorkOrder created = workOrderService.assignWorkOrder(
                complaint.getId(), admin.getId(), "HIGH", null);

        assertThatThrownBy(() -> workOrderService.updateWorkOrderStatus(
                created.getId(), "COMPLETED", "Done"))
                .isInstanceOf(ResponseStatusException.class);

        WorkOrder unchanged = workOrderRepository
                .findById(created.getId()).orElseThrow();

        Complaint unchangedComplaint = complaintRepository
                .findById(complaint.getId()).orElseThrow();

        assertThat(unchanged.getStatus())
                .isEqualTo(WorkOrder.STATUS_ASSIGNED);
        assertThat(unchangedComplaint.getStatus())
                .isEqualTo(Status.ASSIGNED);

        assertThat(complaintHistoryRepository.count()).isEqualTo(1);
    }

    private User user(String email, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setName(email);
        user.setRole(role);
        return user;
    }
}

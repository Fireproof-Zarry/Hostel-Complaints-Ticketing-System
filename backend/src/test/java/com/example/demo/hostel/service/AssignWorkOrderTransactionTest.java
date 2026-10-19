package com.example.demo.hostel.service;

import com.example.demo.hostel.model.Category;
import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.model.Location;
import com.example.demo.hostel.model.Role;
import com.example.demo.hostel.model.Status;
import com.example.demo.hostel.model.User;
import com.example.demo.hostel.model.WorkOrder;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:assign-work-order-test",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class AssignWorkOrderTransactionTest {

    @Autowired
    private ComplaintTransitionService transitionService;

    @Autowired
    private ComplaintRepository complaintRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private LocationRepository locationRepository;

    @MockitoSpyBean
    private WorkOrderRepository workOrderRepository;

    private Long complaintId;
    private String adminId;

    @BeforeEach
    void setUp() {
        User student = userRepository.save(user("student@smail.iitm.ac.in", Role.STUDENT));
        adminId = userRepository.save(user("admin@smail.iitm.ac.in", Role.ADMIN)).getId();

        Category category = new Category();
        category.setName("Electrical");
        category.setDefaultSlaHours(48);

        Location location = new Location();
        location.setFloor("Second");
        location.setRoomNumber("301");

        Complaint complaint = new Complaint();
        complaint.setTitle("Broken fan");
        complaint.setDescription("Not spinning");
        complaint.setCategory(categoryRepository.save(category));
        complaint.setLocation(locationRepository.save(location));
        complaint.setStudent(student);
        complaintId = complaintRepository.save(complaint).getId();
    }

    @AfterEach
    void tearDown() {
        workOrderRepository.deleteAll();
        complaintRepository.deleteAll();
        categoryRepository.deleteAll();
        locationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void assignWorkOrder_updatesComplaintAndCreatesWorkOrder() {
        LocalDateTime deadline = LocalDateTime.now().plusDays(2);

        WorkOrder workOrder = transitionService.assignWorkOrder(complaintId, adminId, " high ", deadline);

        assertThat(workOrder.getId()).isNotNull();
        assertThat(workOrder.getPriority()).isEqualTo("HIGH");
        assertThat(workOrder.getStatus()).isEqualTo(WorkOrder.STATUS_OPEN);
        assertThat(workOrderRepository.count()).isEqualTo(1);

        Complaint complaint = complaintRepository.findById(complaintId).orElseThrow();
        assertThat(complaint.getStatus()).isEqualTo(Status.IN_PROGRESS);
        assertThat(complaint.getAssignedTo()).isEqualTo("admin@smail.iitm.ac.in");
    }

    @Test
    void assignWorkOrder_rollsBackComplaintStatusWhenWorkOrderSaveFails() {
        doThrow(new IllegalStateException("simulated failure"))
                .when(workOrderRepository).save(any(WorkOrder.class));

        assertThatThrownBy(() ->
                transitionService.assignWorkOrder(complaintId, adminId, "HIGH", null)
        ).hasMessageContaining("simulated failure");

        Complaint complaint = complaintRepository.findById(complaintId).orElseThrow();
        assertThat(complaint.getStatus()).isEqualTo(Status.PENDING);
        assertThat(complaint.getAssignedTo()).isNull();
        assertThat(workOrderRepository.count()).isZero();
    }

    private User user(String email, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setName(email);
        user.setRole(role);
        return user;
    }
}

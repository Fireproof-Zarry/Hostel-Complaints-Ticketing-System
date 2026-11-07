package com.example.demo.hostel.service;

import com.example.demo.hostel.dto.StaffMemberView;
import com.example.demo.hostel.model.Category;
import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.model.Location;
import com.example.demo.hostel.model.Role;
import com.example.demo.hostel.model.Status;
import com.example.demo.hostel.model.User;
import com.example.demo.hostel.model.WorkOrderPriority;
import com.example.demo.hostel.model.WorkOrderStatus;
import com.example.demo.hostel.repository.CategoryRepository;
import com.example.demo.hostel.repository.ComplaintRepository;
import com.example.demo.hostel.repository.LocationRepository;
import com.example.demo.hostel.repository.UserRepository;
import com.example.demo.hostel.repository.WorkOrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:staff-test",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "app.auth.admin-emails=admin@smail.iitm.ac.in"
})
class StaffServiceIntegrationTest {

    private static final String ADMIN = "admin@smail.iitm.ac.in";

    @Autowired private StaffService staffService;
    @Autowired private WorkOrderService workOrderService;
    @Autowired private UserRepository userRepository;
    @Autowired private ComplaintRepository complaintRepository;
    @Autowired private WorkOrderRepository workOrderRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoBean private FcmNotificationService notificationService;

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
    void addsStaffByEmailBeforeTheirFirstLogin() {
        StaffMemberView added = staffService.addStaff("  Warden@Gmail.com ");

        assertThat(added.email()).isEqualTo("warden@gmail.com");
        assertThat(userRepository.findByEmail("warden@gmail.com").orElseThrow().getRole()).isEqualTo(Role.STAFF);
        assertThat(staffService.listStaff()).extracting(StaffMemberView::email).containsExactly("warden@gmail.com");
    }

    @Test
    void addingTheSameEmailTwiceIsHarmless() {
        staffService.addStaff("warden@gmail.com");
        staffService.addStaff("warden@gmail.com");

        assertThat(staffService.listStaff()).hasSize(1);
    }

    @Test
    void rejectsInvalidEmailsAndAdministrators() {
        assertStatus(() -> staffService.addStaff("not-an-email"), HttpStatus.BAD_REQUEST);
        assertStatus(() -> staffService.addStaff(""), HttpStatus.BAD_REQUEST);
        assertStatus(() -> staffService.addStaff(ADMIN), HttpStatus.CONFLICT);
    }

    @Test
    void removingStaffCancelsTheirWorkAndReturnsComplaintsToPending() {
        User admin = userRepository.save(user(ADMIN, Role.ADMIN));
        StaffMemberView warden = staffService.addStaff("warden@gmail.com");
        Long complaintId = complaint(userRepository.save(user("student@smail.iitm.ac.in", Role.STUDENT)));
        workOrderService.assignInCharge(complaintId, null, WorkOrderPriority.HIGH, null, "warden@gmail.com");
        assertThat(member("warden@gmail.com").activeWorkOrders()).isEqualTo(1);

        staffService.removeStaff(warden.id(), null, admin.getEmail());

        Complaint complaint = complaintRepository.findById(complaintId).orElseThrow();
        assertThat(complaint.getStatus()).isEqualTo(Status.PENDING);
        assertThat(complaint.getAssignedTo()).isNull();
        assertThat(workOrderRepository.findAll()).extracting(w -> w.getStatus()).containsExactly(WorkOrderStatus.CANCELLED);
        assertThat(userRepository.findById(warden.id()).orElseThrow().getRole()).isEqualTo(Role.STUDENT);
        assertThat(staffService.listStaff()).extracting(StaffMemberView::email).containsExactly(ADMIN);
    }

    @Test
    void removingStaffCanHandTheirWorkToSomeoneElse() {
        User admin = userRepository.save(user(ADMIN, Role.ADMIN));
        StaffMemberView warden = staffService.addStaff("warden@gmail.com");
        StaffMemberView caretaker = staffService.addStaff("caretaker@gmail.com");
        Long complaintId = complaint(userRepository.save(user("student@smail.iitm.ac.in", Role.STUDENT)));
        workOrderService.assignInCharge(complaintId, null, WorkOrderPriority.HIGH, null, "warden@gmail.com");

        staffService.removeStaff(warden.id(), caretaker.id(), admin.getEmail());

        Complaint complaint = complaintRepository.findById(complaintId).orElseThrow();
        assertThat(complaint.getStatus()).isEqualTo(Status.ASSIGNED);
        assertThat(complaint.getAssignedTo()).isEqualTo("caretaker@gmail.com");
        assertThat(staffService.getActiveWorkOrders(caretaker.id()))
                .extracting(w -> w.complaintId(), w -> w.status())
                .containsExactly(org.assertj.core.groups.Tuple.tuple(complaintId, WorkOrderStatus.OPEN));
        assertThat(userRepository.findById(warden.id()).orElseThrow().getRole()).isEqualTo(Role.STUDENT);
    }

    @Test
    void theNewAssigneeIsToldWhenWorkIsHandedOver() {
        User admin = userRepository.save(user(ADMIN, Role.ADMIN));
        StaffMemberView warden = staffService.addStaff("warden@gmail.com");
        User caretaker = user("caretaker@gmail.com", Role.STAFF);
        caretaker.setFcmToken("caretaker-token");
        caretaker = userRepository.save(caretaker);
        User student = userRepository.save(user("student@smail.iitm.ac.in", Role.STUDENT));
        workOrderService.assignInCharge(complaint(student), null, WorkOrderPriority.HIGH, null, "warden@gmail.com");
        workOrderService.assignInCharge(complaint(student), null, WorkOrderPriority.LOW, null, "warden@gmail.com");

        staffService.removeStaff(warden.id(), caretaker.getId(), admin.getEmail());

        verify(notificationService).sendAssignmentNotification("caretaker-token", "2 complaints were assigned to you.");
    }

    @Test
    void cannotReassignToThePersonBeingRemovedOrToAStudent() {
        userRepository.save(user(ADMIN, Role.ADMIN));
        StaffMemberView warden = staffService.addStaff("warden@gmail.com");
        User student = userRepository.save(user("student@smail.iitm.ac.in", Role.STUDENT));

        assertStatus(() -> staffService.removeStaff(warden.id(), warden.id(), ADMIN), HttpStatus.BAD_REQUEST);
        assertStatus(() -> staffService.removeStaff(warden.id(), student.getId(), ADMIN), HttpStatus.BAD_REQUEST);
        assertThat(userRepository.findById(warden.id()).orElseThrow().getRole()).isEqualTo(Role.STAFF);
    }

    @Test
    void listsAdminsTooWithOverdueWorkAndPendingInvites() {
        User admin = user(ADMIN, Role.ADMIN);
        admin.setName("Hostel Admin");
        userRepository.save(admin);
        staffService.addStaff("warden@gmail.com");
        User student = userRepository.save(user("student@smail.iitm.ac.in", Role.STUDENT));
        workOrderService.assignInCharge(complaint(student), null, WorkOrderPriority.HIGH, null, "warden@gmail.com");
        workOrderService.assignInCharge(complaint(student), null, WorkOrderPriority.LOW,
                LocalDateTime.now().plusDays(3), "warden@gmail.com");
        // Past deadlines are rejected when assigning, so let one lapse directly
        var lapsed = workOrderRepository.findAll().stream().filter(w -> w.getDeadline() == null).findFirst().orElseThrow();
        lapsed.setDeadline(LocalDateTime.now().minusDays(2));
        workOrderRepository.save(lapsed);

        StaffMemberView warden = member("warden@gmail.com");
        assertThat(warden.role()).isEqualTo(Role.STAFF);
        assertThat(warden.activeWorkOrders()).isEqualTo(2);
        assertThat(warden.overdueWorkOrders()).isEqualTo(1);
        assertThat(warden.invitePending()).isTrue();
        StaffMemberView listedAdmin = member(ADMIN);
        assertThat(listedAdmin.role()).isEqualTo(Role.ADMIN);
        assertThat(listedAdmin.invitePending()).isFalse();
        assertThat(staffService.getActiveWorkOrders(warden.id()))
                .extracting(w -> w.overdue())
                .containsExactly(true, false);
    }

    @Test
    void removingSomeoneWhoIsNotStaffIsNotFound() {
        User student = userRepository.save(user("student@smail.iitm.ac.in", Role.STUDENT));

        assertStatus(() -> staffService.removeStaff(student.getId(), null, ADMIN), HttpStatus.NOT_FOUND);
    }

    private StaffMemberView member(String email) {
        return staffService.listStaff().stream().filter(m -> m.email().equals(email)).findFirst().orElseThrow();
    }

    private Long complaint(User student) {
        Category category = categoryRepository.findAll().stream().findFirst().orElseGet(() -> {
            Category created = new Category();
            created.setName("Electrical");
            created.setDefaultSlaHours(48);
            return created;
        });
        Location location = new Location();
        location.setFloor("First");
        location.setRoomNumber("204");
        Complaint complaint = new Complaint();
        complaint.setTitle("Fan not working");
        complaint.setDescription("Staff test");
        complaint.setCategory(categoryRepository.save(category));
        complaint.setLocation(locationRepository.save(location));
        complaint.setStudent(student);
        return complaintRepository.save(complaint).getId();
    }

    private static void assertStatus(Runnable call, HttpStatus expected) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(expected));
    }

    private static User user(String email, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setName(email);
        user.setRole(role);
        return user;
    }
}

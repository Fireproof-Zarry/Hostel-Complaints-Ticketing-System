package com.example.demo.hostel.controller;

import com.example.demo.hostel.model.Category;
import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.model.Location;
import com.example.demo.hostel.model.Role;
import com.example.demo.hostel.model.User;
import com.example.demo.hostel.repository.CategoryRepository;
import com.example.demo.hostel.repository.ComplaintRepository;
import com.example.demo.hostel.repository.LocationRepository;
import com.example.demo.hostel.repository.UserRepository;
import com.example.demo.hostel.service.FcmNotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Assigning through the endpoint the Assign dialog uses. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:assignment-notification-test",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "app.auth.admin-emails=admin@smail.iitm.ac.in"
})
@AutoConfigureMockMvc
class AssignmentNotificationApiIntegrationTest {

    private static final String ADMIN = "admin@smail.iitm.ac.in";
    private static final String STAFF_A = "ravi@gmail.com";
    private static final String STAFF_B = "meena@gmail.com";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ComplaintRepository complaintRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoBean private FcmNotificationService notificationService;

    private Long complaintId;

    @BeforeEach
    void setUp() {
        userRepository.save(user(ADMIN, Role.ADMIN, "admin-token"));
        userRepository.save(user(STAFF_A, Role.STAFF, "a-token"));
        userRepository.save(user(STAFF_B, Role.STAFF, "b-token"));
        User student = userRepository.save(user("student@smail.iitm.ac.in", Role.STUDENT, null));

        Category category = new Category();
        category.setName("Electrical");
        category.setDefaultSlaHours(48);
        Location location = new Location();
        location.setFloor("First");
        location.setRoomNumber("204");

        Complaint complaint = new Complaint();
        complaint.setTitle("Fan not working");
        complaint.setDescription("Notification test");
        complaint.setCategory(categoryRepository.save(category));
        complaint.setLocation(locationRepository.save(location));
        complaint.setStudent(student);
        complaintId = complaintRepository.save(complaint).getId();
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
    void assigningAToBAndBackToANotifiesEachNewAssignee() throws Exception {
        assign(STAFF_A);
        assign(STAFF_B);
        assign(STAFF_A);

        verify(notificationService, times(2)).sendAssignmentNotification(eq("a-token"), anyString());
        verify(notificationService, times(1)).sendAssignmentNotification(eq("b-token"), anyString());
    }

    @Test
    void goingThroughTheSignedInAdminStillNotifiesTheStaffMemberAfterwards() throws Exception {
        assign(STAFF_A);
        assign(ADMIN);
        assign(STAFF_A);

        verify(notificationService, times(2)).sendAssignmentNotification(eq("a-token"), anyString());
    }

    private void assign(String email) throws Exception {
        mockMvc.perform(patch("/api/admin/complaints/{id}/assign", complaintId)
                        .param("email", email)
                        .param("priority", "MEDIUM")
                        .with(jwt().jwt(token -> token.claim("email", ADMIN))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    private static User user(String email, Role role, String token) {
        User user = new User();
        user.setEmail(email);
        user.setName(email);
        user.setRole(role);
        user.setFcmToken(token);
        return user;
    }
}

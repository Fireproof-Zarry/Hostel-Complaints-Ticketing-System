package com.example.demo.hostel.controller;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end check of the admin status endpoint against a real (H2) database:
 * the status change and its complaint_history row commit together or not at all.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:admin-status-update-test",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureMockMvc
class AdminStatusUpdateIntegrationTest {

    private static final String ADMIN_EMAIL = "admin@smail.iitm.ac.in";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ComplaintRepository complaintRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoSpyBean
    private ComplaintHistoryRepository complaintHistoryRepository;

    private Long complaintId;
    private String adminId;

    @BeforeEach
    void setUp() {
        User student = userRepository.save(user("student@smail.iitm.ac.in", Role.STUDENT));
        adminId = userRepository.save(user(ADMIN_EMAIL, Role.ADMIN)).getId();

        Category category = new Category();
        category.setName("Electrical");
        category.setDefaultSlaHours(48);

        Location location = new Location();
        location.setFloor("Third");
        location.setRoomNumber("402");

        Complaint complaint = new Complaint();
        complaint.setTitle("Fan not working");
        complaint.setDescription("Stopped this morning");
        complaint.setCategory(categoryRepository.save(category));
        complaint.setLocation(locationRepository.save(location));
        complaint.setStudent(student);
        complaintId = complaintRepository.save(complaint).getId();
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
    void adminStatusUpdate_savesComplaintAndHistory() throws Exception {
        mockMvc.perform(patch("/api/admin/complaints/{id}/status", complaintId)
                        .param("status", "IN_PROGRESS")
                        .param("reason", "Electrician on the way")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(complaintId))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        assertThat(complaintRepository.findById(complaintId).orElseThrow().getStatus())
                .isEqualTo(Status.IN_PROGRESS);

        // History associations are lazy, so read them inside a transaction
        transactionTemplate.executeWithoutResult(tx -> {
            List<ComplaintHistory> history = complaintHistoryRepository.findAll();
            assertThat(history).hasSize(1);

            ComplaintHistory entry = history.getFirst();
            assertThat(entry.getComplaint().getId()).isEqualTo(complaintId);
            assertThat(entry.getActor().getId()).isEqualTo(adminId);
            assertThat(entry.getOldStatus()).isEqualTo(Status.PENDING);
            assertThat(entry.getNewStatus()).isEqualTo(Status.IN_PROGRESS);
            assertThat(entry.getReason()).isEqualTo("Electrician on the way");
        });
    }

    @Test
    void adminStatusUpdate_rollsBackComplaintWhenHistorySaveFails() {
        doThrow(new RuntimeException("simulated history failure"))
                .when(complaintHistoryRepository).save(any(ComplaintHistory.class));

        // The exception escapes the controller, so MockMvc rethrows it
        assertThatThrownBy(() ->
                mockMvc.perform(patch("/api/admin/complaints/{id}/status", complaintId)
                        .param("status", "RESOLVED")
                        .with(adminJwt()))
        ).hasRootCauseMessage("simulated history failure");

        assertThat(complaintRepository.findById(complaintId).orElseThrow().getStatus())
                .isEqualTo(Status.PENDING);
        assertThat(complaintHistoryRepository.count()).isZero();
    }

    @Test
    void studentCannotUpdateStatus() throws Exception {
        mockMvc.perform(patch("/api/admin/complaints/{id}/status", complaintId)
                        .param("status", "RESOLVED")
                        .with(jwt()
                                .jwt(token -> token.claim("email", "student@smail.iitm.ac.in"))
                                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isForbidden());

        assertThat(complaintRepository.findById(complaintId).orElseThrow().getStatus())
                .isEqualTo(Status.PENDING);
        assertThat(complaintHistoryRepository.count()).isZero();
    }

    private static JwtRequestPostProcessor adminJwt() {
        return jwt()
                .jwt(token -> token.claim("email", ADMIN_EMAIL))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static User user(String email, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setName(email);
        user.setRole(role);
        return user;
    }
}

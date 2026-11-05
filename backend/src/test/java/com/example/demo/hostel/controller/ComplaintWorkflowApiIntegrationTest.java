package com.example.demo.hostel.controller;

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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The status workflow and history timeline over HTTP, through the real security chain. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:complaint-workflow-test",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureMockMvc
class ComplaintWorkflowApiIntegrationTest {

    private static final String OWNER = "owner@smail.iitm.ac.in";
    private static final String OTHER_STUDENT = "other@smail.iitm.ac.in";
    private static final String ADMIN = "admin@smail.iitm.ac.in";
    private static final String STAFF = "warden@gmail.com";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ComplaintRepository complaintRepository;
    @Autowired private ComplaintHistoryRepository historyRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Long complaintId;

    @BeforeEach
    void setUp() {
        User owner = userRepository.save(user(OWNER, Role.STUDENT, "Asha"));
        userRepository.save(user(OTHER_STUDENT, Role.STUDENT, "Other"));
        userRepository.save(user(ADMIN, Role.ADMIN, "Meera Admin"));
        userRepository.save(user(STAFF, Role.STAFF, "Ravi Staff"));

        Category category = new Category();
        category.setName("Electrical");
        category.setDefaultSlaHours(48);
        Location location = new Location();
        location.setFloor("First");
        location.setRoomNumber("204");

        Complaint complaint = new Complaint();
        complaint.setTitle("Fan not working");
        complaint.setDescription("Workflow test");
        complaint.setCategory(categoryRepository.save(category));
        complaint.setLocation(locationRepository.save(location));
        complaint.setStudent(owner);
        complaintId = complaintRepository.save(complaint).getId();
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM complaint_history");
        jdbcTemplate.update("DELETE FROM complaints");
        categoryRepository.deleteAll();
        locationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void jumpingFromPendingToResolvedIsRefusedWithAReason() throws Exception {
        mockMvc.perform(patch("/api/admin/complaints/{id}/status", complaintId)
                        .param("status", "RESOLVED").with(as(ADMIN, "ROLE_ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A complaint cannot move from PENDING to RESOLVED"));

        assertThat(complaintRepository.findById(complaintId).orElseThrow().getStatus()).isEqualTo(Status.PENDING);
        assertThat(historyRepository.count()).isZero();
    }

    @Test
    void settingTheCurrentStatusAgainIsRefusedAndLogsNothing() throws Exception {
        mockMvc.perform(patch("/api/admin/complaints/{id}/status", complaintId)
                        .param("status", "PENDING").with(as(ADMIN, "ROLE_ADMIN")))
                .andExpect(status().isConflict());

        assertThat(historyRepository.count()).isZero();
    }

    @Test
    void staffCanReopenAClosedComplaint() throws Exception {
        change("IN_PROGRESS", STAFF, "ROLE_STAFF");
        change("RESOLVED", STAFF, "ROLE_STAFF");

        change("PENDING", STAFF, "ROLE_STAFF");

        assertThat(complaintRepository.findById(complaintId).orElseThrow().getStatus()).isEqualTo(Status.PENDING);
    }

    @Test
    void studentsSeeTheirHistoryNewestFirstWithStaffHidden() throws Exception {
        change("IN_PROGRESS", ADMIN, "ROLE_ADMIN");
        change("RESOLVED", STAFF, "ROLE_STAFF");

        mockMvc.perform(get("/api/complaints/{id}/history", complaintId).with(as(OWNER, "ROLE_STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].newStatus").value("RESOLVED"))
                .andExpect(jsonPath("$[1].newStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$[0].changedBy").value("Hostel office"))
                .andExpect(jsonPath("$[1].changedBy").value("Hostel office"));
    }

    @Test
    void adminsAndStaffCanReadAnyHistoryWithRealNames() throws Exception {
        change("IN_PROGRESS", ADMIN, "ROLE_ADMIN");
        change("RESOLVED", STAFF, "ROLE_STAFF");

        for (JwtRequestPostProcessor viewer : new JwtRequestPostProcessor[] {
                as(ADMIN, "ROLE_ADMIN"), as(STAFF, "ROLE_STAFF")}) {
            String body = mockMvc.perform(get("/api/complaints/{id}/history", complaintId).with(viewer))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].changedBy").value("Ravi Staff"))
                    .andExpect(jsonPath("$[1].changedBy").value("Meera Admin"))
                    .andReturn().getResponse().getContentAsString();

            assertThat(body).doesNotContain(STAFF).doesNotContain(ADMIN);
        }
    }

    @Test
    void otherStudentsGetNotFoundAndSignedOutUsersAreRefused() throws Exception {
        mockMvc.perform(get("/api/complaints/{id}/history", complaintId).with(as(OTHER_STUDENT, "ROLE_STUDENT")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/complaints/{id}/history", complaintId))
                .andExpect(status().isUnauthorized());
    }

    private void change(String newStatus, String email, String role) throws Exception {
        mockMvc.perform(patch("/api/admin/complaints/{id}/status", complaintId)
                        .param("status", newStatus).with(as(email, role)))
                .andExpect(status().isOk());
    }

    private static JwtRequestPostProcessor as(String email, String role) {
        return jwt().jwt(token -> token.claim("email", email)).authorities(new SimpleGrantedAuthority(role));
    }

    private static User user(String email, Role role, String name) {
        User user = new User();
        user.setEmail(email);
        user.setName(name);
        user.setRole(role);
        return user;
    }
}

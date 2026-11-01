package com.example.demo.hostel.controller;

import com.example.demo.hostel.model.Category;
import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.model.Location;
import com.example.demo.hostel.model.Role;
import com.example.demo.hostel.model.User;
import com.example.demo.hostel.repository.CategoryRepository;
import com.example.demo.hostel.repository.ComplaintHistoryRepository;
import com.example.demo.hostel.repository.ComplaintRepository;
import com.example.demo.hostel.repository.LocationRepository;
import com.example.demo.hostel.repository.UserRepository;
import com.example.demo.hostel.service.ComplaintTransitionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/complaints/{id}/history over HTTP: the JSON must not expose the
 * admin who made the change (email, phone) or nest entity internals.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:complaint-history-response-test",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureMockMvc
class ComplaintHistoryResponseTest {

    private static final String STUDENT_EMAIL = "student@smail.iitm.ac.in";
    private static final String ADMIN_EMAIL = "warden@smail.iitm.ac.in";
    private static final String ADMIN_PHONE = "9876543210";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ComplaintTransitionService transitionService;

    @Autowired
    private ComplaintRepository complaintRepository;

    @Autowired
    private ComplaintHistoryRepository historyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private LocationRepository locationRepository;

    private Long complaintId;

    @BeforeEach
    void setUp() {
        User student = new User();
        student.setEmail(STUDENT_EMAIL);
        student.setName("Student");
        student.setRole(Role.STUDENT);
        student = userRepository.save(student);

        User admin = new User();
        admin.setEmail(ADMIN_EMAIL);
        admin.setName("Warden");
        admin.setRole(Role.ADMIN);
        admin.setPhoneNumber(ADMIN_PHONE);
        admin = userRepository.save(admin);

        Category category = new Category();
        category.setName("Plumbing");
        category.setDefaultSlaHours(48);

        Location location = new Location();
        location.setFloor("First");
        location.setRoomNumber("204");

        Complaint complaint = new Complaint();
        complaint.setTitle("Leaking tap");
        complaint.setDescription("Dripping water");
        complaint.setCategory(categoryRepository.save(category));
        complaint.setLocation(locationRepository.save(location));
        complaint.setStudent(student);
        complaintId = complaintRepository.save(complaint).getId();

        transitionService.changeComplaintStatus(complaintId, admin.getId(), "IN_PROGRESS", "Plumber assigned");
    }

    @AfterEach
    void tearDown() {
        historyRepository.deleteAll();
        complaintRepository.deleteAll();
        categoryRepository.deleteAll();
        locationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void historyJsonHidesTheAdminsAccountDetails() throws Exception {
        mockMvc.perform(get("/api/complaints/{id}/history", complaintId)
                        .with(jwt().jwt(token -> token.claim("email", STUDENT_EMAIL))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].oldStatus").value("PENDING"))
                .andExpect(jsonPath("$[0].newStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$[0].reason").value("Plumber assigned"))
                .andExpect(jsonPath("$[0].changedAt").exists())
                .andExpect(jsonPath("$[0].changedBy").value("Hostel office"))
                .andExpect(jsonPath("$[0].actor").doesNotExist())
                .andExpect(jsonPath("$[0].complaint").doesNotExist())
                .andExpect(content().string(not(containsString(ADMIN_EMAIL))))
                .andExpect(content().string(not(containsString(ADMIN_PHONE))))
                .andExpect(content().string(not(containsString("hibernateLazyInitializer"))));
    }

    @Test
    void nonOwnersCannotReadTheHistory() throws Exception {
        mockMvc.perform(get("/api/complaints/{id}/history", complaintId)
                        .with(jwt().jwt(token -> token.claim("email", ADMIN_EMAIL))))
                .andExpect(status().isNotFound());
    }
}

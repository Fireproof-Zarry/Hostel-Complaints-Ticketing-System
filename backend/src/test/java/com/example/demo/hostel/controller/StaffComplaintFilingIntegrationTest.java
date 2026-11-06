package com.example.demo.hostel.controller;

import com.example.demo.hostel.model.Category;
import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.model.Location;
import com.example.demo.hostel.model.Role;
import com.example.demo.hostel.model.Status;
import com.example.demo.hostel.model.User;
import com.example.demo.hostel.repository.CategoryRepository;
import com.example.demo.hostel.repository.ComplaintRepository;
import com.example.demo.hostel.repository.LocationRepository;
import com.example.demo.hostel.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Staff and admins filing complaints, and the open-complaint limits, over HTTP. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:staff-filing-test",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "app.auth.admin-emails=admin@smail.iitm.ac.in"
})
@AutoConfigureMockMvc
class StaffComplaintFilingIntegrationTest {

    private static final String STAFF = "warden@gmail.com";
    private static final String ADMIN = "admin@smail.iitm.ac.in";
    private static final String STUDENT = "student@smail.iitm.ac.in";

    private static final String ROOM_BODY = """
            {"title":"Geyser leaking","description":"Empty room","category":"Plumbing",
             "isCommunityArea":false,"room":"%s"}
            """;
    private static final String COMMUNITY_BODY = """
            {"title":"Corridor light out","description":"Near the stairs","category":"Electrical",
             "isCommunityArea":true,"floor":"First","wing":"Manager's office"}
            """;

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ComplaintRepository complaintRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private User staff;
    private User student;
    private Category plumbing;
    private Location room;

    @BeforeEach
    void setUp() {
        staff = userRepository.save(user(STAFF, Role.STAFF, null));
        userRepository.save(user(ADMIN, Role.ADMIN, null));
        student = userRepository.save(user(STUDENT, Role.STUDENT, "305"));

        Category electrical = new Category();
        electrical.setName("Electrical");
        electrical.setDefaultSlaHours(48);
        categoryRepository.save(electrical);
        Category newPlumbing = new Category();
        newPlumbing.setName("Plumbing");
        newPlumbing.setDefaultSlaHours(48);
        plumbing = categoryRepository.save(newPlumbing);

        Location location = new Location();
        location.setFloor("Second");
        location.setRoomNumber("305");
        room = locationRepository.save(location);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM complaints");
        categoryRepository.deleteAll();
        locationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void staffCanFileACommunityComplaintAndSeeItAsTheirs() throws Exception {
        file(COMMUNITY_BODY, as(STAFF, "ROLE_STAFF"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.communityArea").value(true))
                .andExpect(jsonPath("$.status").value("PENDING"));

        mockMvc.perform(get("/api/complaints/mine").with(as(STAFF, "ROLE_STAFF")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Corridor light out"));
    }

    @Test
    void staffCanFileAboutAnyRoomThoughTheyHaveNoneOfTheirOwn() throws Exception {
        file(ROOM_BODY.formatted("204"), as(STAFF, "ROLE_STAFF"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.room").value("204"))
                .andExpect(jsonPath("$.floor").value("First"))
                .andExpect(jsonPath("$.communityArea").value(false));
    }

    @Test
    void adminsCanFileToo() throws Exception {
        file(ROOM_BODY.formatted("204"), as(ADMIN, "ROLE_ADMIN")).andExpect(status().isCreated());
    }

    @Test
    void anInvalidRoomIsRefused() throws Exception {
        file(ROOM_BODY.formatted("999"), as(STAFF, "ROLE_STAFF")).andExpect(status().isUnprocessableContent());
        assertThat(complaintRepository.count()).isZero();
    }

    @Test
    void aStudentCannotFileForAnotherRoom() throws Exception {
        file(ROOM_BODY.formatted("204"), as(STUDENT, "ROLE_STUDENT"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.room").value("305"));
    }

    @Test
    void assignedComplaintsCountTowardsAStudentsLimitOfFive() throws Exception {
        for (int i = 0; i < 5; i++) {
            existing(student, Status.ASSIGNED);
        }

        file(ROOM_BODY.formatted("305"), as(STUDENT, "ROLE_STUDENT")).andExpect(status().isTooManyRequests());
    }

    @Test
    void closedComplaintsDoNotCount() throws Exception {
        for (int i = 0; i < 5; i++) {
            existing(student, Status.RESOLVED);
        }

        file(ROOM_BODY.formatted("305"), as(STUDENT, "ROLE_STUDENT")).andExpect(status().isCreated());
    }

    @Test
    void staffGetALargerLimitOfFifty() throws Exception {
        for (int i = 0; i < 49; i++) {
            existing(staff, Status.PENDING);
        }
        file(COMMUNITY_BODY, as(STAFF, "ROLE_STAFF")).andExpect(status().isCreated());

        file(COMMUNITY_BODY, as(STAFF, "ROLE_STAFF")).andExpect(status().isTooManyRequests());
    }

    @Test
    void staffCanOpenAnyComplaintByIdButStudentsCannot() throws Exception {
        Long id = existing(student, Status.PENDING);

        mockMvc.perform(get("/api/admin/complaints/{id}", id).with(as(STAFF, "ROLE_STAFF")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
        mockMvc.perform(get("/api/admin/complaints/{id}", 999_999).with(as(STAFF, "ROLE_STAFF")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/complaints/{id}", id).with(as(STUDENT, "ROLE_STUDENT")))
                .andExpect(status().isForbidden());
    }

    private Long existing(User owner, Status status) {
        Complaint complaint = new Complaint();
        complaint.setTitle("Existing");
        complaint.setDescription("Limit test");
        complaint.setCategory(plumbing);
        complaint.setLocation(room);
        complaint.setStudent(owner);
        complaint.setStatus(status);
        return complaintRepository.save(complaint).getId();
    }

    private ResultActions file(String body, JwtRequestPostProcessor user) throws Exception {
        return mockMvc.perform(post("/api/complaints").with(user)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static JwtRequestPostProcessor as(String email, String role) {
        return jwt().jwt(token -> token.claim("email", email)).authorities(new SimpleGrantedAuthority(role));
    }

    private static User user(String email, Role role, String room) {
        User user = new User();
        user.setEmail(email);
        user.setName(email);
        user.setRole(role);
        user.setRoomNumber(room);
        return user;
    }
}

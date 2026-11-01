package com.example.demo.hostel.service;

import com.example.demo.hostel.dto.CreateComplaintRequest;
import com.example.demo.hostel.model.Category;
import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.model.DeletionReason;
import com.example.demo.hostel.model.Location;
import com.example.demo.hostel.model.Role;
import com.example.demo.hostel.model.Status;
import com.example.demo.hostel.model.User;
import com.example.demo.hostel.repository.CategoryRepository;
import com.example.demo.hostel.repository.ComplaintHistoryRepository;
import com.example.demo.hostel.repository.ComplaintRepository;
import com.example.demo.hostel.repository.LocationRepository;
import com.example.demo.hostel.repository.UpvoteRepository;
import com.example.demo.hostel.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:complaint-deletion-test",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureMockMvc
class ComplaintDeletionIntegrationTest {

    private static final String OWNER_EMAIL = "owner@smail.iitm.ac.in";
    private static final String OTHER_EMAIL = "other@smail.iitm.ac.in";

    @Autowired private ComplaintService complaintService;
    @Autowired private AdminService adminService;
    @Autowired private UpvoteService upvoteService;
    @Autowired private ComplaintTransitionService transitionService;
    @Autowired private ComplaintRepository complaintRepository;
    @Autowired private ComplaintHistoryRepository historyRepository;
    @Autowired private UpvoteRepository upvoteRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private MockMvc mockMvc;

    private User owner;
    private User other;
    private User admin;
    private Category category;
    private Location room;
    private Location washroom;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(user(OWNER_EMAIL, Role.STUDENT));
        owner.setRoomNumber("204");
        owner = userRepository.save(owner);
        other = userRepository.save(user(OTHER_EMAIL, Role.STUDENT));
        admin = userRepository.save(user("admin@smail.iitm.ac.in", Role.ADMIN));

        category = new Category();
        category.setName("Electrical");
        category.setDefaultSlaHours(48);
        category = categoryRepository.save(category);

        room = new Location();
        room.setFloor("First");
        room.setRoomNumber("204");
        room = locationRepository.save(room);

        washroom = new Location();
        washroom.setFloor("First");
        washroom.setWing("East wing washroom");
        washroom.setCommunityArea(true);
        washroom = locationRepository.save(washroom);
    }

    @AfterEach
    void tearDown() {
        upvoteRepository.deleteAll();
        historyRepository.deleteAll();
        // deleteAll cannot see soft-deleted rows; remove every row for the next test
        jdbcTemplate.update("DELETE FROM complaints");
        categoryRepository.deleteAll();
        locationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void ownerDeletesPendingComplaintAndItDisappearsEverywhere() {
        Long id = complaint(owner, room, Status.PENDING);

        complaintService.deleteComplaint(id, OWNER_EMAIL, DeletionReason.TEST_TICKET);

        assertThat(complaintRepository.findById(id)).isEmpty();
        assertThat(complaintService.getMyComplaints(OWNER_EMAIL)).isEmpty();
        assertThat(adminService.getComplaints(null, null, null, null, null, null)).isEmpty();
        assertStatus(() -> complaintService.getComplaintById(id, OWNER_EMAIL), HttpStatus.NOT_FOUND);
        assertStatus(() -> complaintService.getComplaintHistory(id, OWNER_EMAIL), HttpStatus.NOT_FOUND);
        assertStatus(() -> transitionService.changeComplaintStatus(id, admin.getId(), "IN_PROGRESS", null),
                HttpStatus.NOT_FOUND);
    }

    @Test
    void deletionKeepsTheRowWithReasonAndTime() {
        Long id = complaint(owner, room, Status.PENDING);

        complaintService.deleteComplaint(id, OWNER_EMAIL, DeletionReason.PROBLEM_RECTIFIED);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT deleted_at, deletion_reason FROM complaints WHERE id = ?", id);
        assertThat(row.get("DELETED_AT")).isNotNull();
        assertThat(row.get("DELETION_REASON")).isEqualTo("PROBLEM_RECTIFIED");
    }

    @Test
    void onlyPendingComplaintsCanBeDeleted() {
        Long assigned = complaint(owner, room, Status.ASSIGNED);
        Long inProgress = complaint(owner, room, Status.IN_PROGRESS);

        assertStatus(() -> complaintService.deleteComplaint(assigned, OWNER_EMAIL, DeletionReason.TEST_TICKET),
                HttpStatus.CONFLICT);
        assertStatus(() -> complaintService.deleteComplaint(inProgress, OWNER_EMAIL, DeletionReason.TEST_TICKET),
                HttpStatus.CONFLICT);
        assertThat(complaintRepository.findById(assigned)).isPresent();
    }

    @Test
    void onlyTheOwnerCanDelete() {
        Long id = complaint(owner, room, Status.PENDING);

        assertStatus(() -> complaintService.deleteComplaint(id, OTHER_EMAIL, DeletionReason.TEST_TICKET),
                HttpStatus.NOT_FOUND);
        assertThat(complaintRepository.findById(id)).isPresent();
    }

    @Test
    void reasonIsRequired() {
        Long id = complaint(owner, room, Status.PENDING);

        assertStatus(() -> complaintService.deleteComplaint(id, OWNER_EMAIL, null), HttpStatus.BAD_REQUEST);
    }

    @Test
    void deletedComplaintsDoNotCountTowardTheOpenComplaintLimit() {
        Long first = null;
        for (int i = 0; i < 5; i++) {
            Long id = complaint(owner, room, Status.PENDING);
            if (first == null) first = id;
        }
        CreateComplaintRequest request = new CreateComplaintRequest(
                "Sixth", "Another issue", "Electrical", false, null, null, null, null, false);
        assertStatus(() -> complaintService.createComplaint(OWNER_EMAIL, request), HttpStatus.TOO_MANY_REQUESTS);

        complaintService.deleteComplaint(first, OWNER_EMAIL, DeletionReason.WRONGFUL_SUBMISSION);

        assertThat(complaintService.createComplaint(OWNER_EMAIL, request).getId()).isNotNull();
    }

    @Test
    void deletedCommunityComplaintCannotBeUpvoted() {
        Long id = complaint(owner, washroom, Status.PENDING);
        complaintService.deleteComplaint(id, OWNER_EMAIL, DeletionReason.INCORRECT_DETAILS);

        assertStatus(() -> upvoteService.toggleUpvote(id, other.getId()), HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteEndpointNeedsAValidReason() throws Exception {
        Long id = complaint(owner, room, Status.PENDING);

        mockMvc.perform(delete("/api/complaints/{id}", id)
                        .with(jwt().jwt(t -> t.claim("email", OWNER_EMAIL))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(delete("/api/complaints/{id}", id)
                        .param("reason", "BORED")
                        .with(jwt().jwt(t -> t.claim("email", OWNER_EMAIL))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(delete("/api/complaints/{id}", id)
                        .param("reason", "TEST_TICKET")
                        .with(jwt().jwt(t -> t.claim("email", OWNER_EMAIL))))
                .andExpect(status().isNoContent());

        assertThat(complaintRepository.findById(id)).isEmpty();
    }

    private Long complaint(User student, Location location, Status status) {
        Complaint complaint = new Complaint();
        complaint.setTitle("Fan not working");
        complaint.setDescription("Deletion test");
        complaint.setCategory(category);
        complaint.setLocation(location);
        complaint.setStudent(student);
        complaint.setStatus(status);
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

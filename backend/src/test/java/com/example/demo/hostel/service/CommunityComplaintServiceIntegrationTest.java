package com.example.demo.hostel.service;

import com.example.demo.hostel.dto.CommunityComplaintPage;
import com.example.demo.hostel.dto.CommunityComplaintView;
import com.example.demo.hostel.model.Category;
import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.model.Location;
import com.example.demo.hostel.model.Role;
import com.example.demo.hostel.model.Status;
import com.example.demo.hostel.model.User;
import com.example.demo.hostel.repository.CategoryRepository;
import com.example.demo.hostel.repository.ComplaintRepository;
import com.example.demo.hostel.repository.LocationRepository;
import com.example.demo.hostel.repository.UpvoteRepository;
import com.example.demo.hostel.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:community-dashboard-test",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class CommunityComplaintServiceIntegrationTest {

    private static final String VIEWER_EMAIL = "viewer@smail.iitm.ac.in";

    @Autowired
    private CommunityComplaintService communityComplaintService;

    @Autowired
    private UpvoteService upvoteService;

    @Autowired
    private ComplaintRepository complaintRepository;

    @Autowired
    private UpvoteRepository upvoteRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User viewer;
    private User reporter;
    private List<User> voters;
    private Category category;

    private Long washroom;      // 3 upvotes, First floor
    private Long dispenser;     // 1 upvote (by viewer), Second floor
    private Long corridorLight; // 0 upvotes, reported by viewer, First floor, newest
    private Long personalRoom;  // personal: never on the dashboard
    private Long resolved;      // resolved: not open
    private Long archived;      // archived

    @BeforeEach
    void setUp() {
        viewer = userRepository.save(user(VIEWER_EMAIL));
        reporter = userRepository.save(user("reporter@smail.iitm.ac.in"));
        voters = List.of(
                userRepository.save(user("v1@smail.iitm.ac.in")),
                userRepository.save(user("v2@smail.iitm.ac.in")),
                userRepository.save(user("v3@smail.iitm.ac.in")));

        category = new Category();
        category.setName("Cleaning");
        category.setDefaultSlaHours(24);
        category = categoryRepository.save(category);

        Location firstWashroom = communityArea("First", "East wing washroom");
        Location secondDispenser = communityArea("Second", "Water dispenser");
        Location firstCorridor = communityArea("First", "Corridor");
        Location room204 = new Location();
        room204.setFloor("First");
        room204.setRoomNumber("204");
        room204 = locationRepository.save(room204);

        washroom = complaint("Washroom not cleaned", reporter, Status.PENDING, firstWashroom, 3);
        dispenser = complaint("Dispenser leaking", reporter, Status.IN_PROGRESS, secondDispenser, 2);
        corridorLight = complaint("Corridor light broken", viewer, Status.ASSIGNED, firstCorridor, 1);
        personalRoom = complaint("Fan not working", reporter, Status.PENDING, room204, 4);
        resolved = complaint("Old washroom issue", reporter, Status.RESOLVED, firstWashroom, 5);
        archived = complaint("Archived corridor issue", reporter, Status.PENDING, firstCorridor, 6);
        Complaint toArchive = complaintRepository.findById(archived).orElseThrow();
        toArchive.setArchived(true);
        complaintRepository.save(toArchive);

        for (User voter : voters) {
            upvoteService.toggleUpvote(washroom, voter.getId());
        }
        upvoteService.toggleUpvote(dispenser, viewer.getId());
    }

    @AfterEach
    void tearDown() {
        upvoteRepository.deleteAll();
        // deleteAll cannot see soft-deleted rows
        jdbcTemplate.update("DELETE FROM complaints");
        categoryRepository.deleteAll();
        locationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void showsOnlyOpenNonArchivedCommunityComplaintsMostUpvotedFirst() {
        CommunityComplaintPage page = communityComplaintService.getCommunityComplaints(VIEWER_EMAIL, null, "upvotes", 0, 20);

        assertThat(ids(page)).containsExactly(washroom, dispenser, corridorLight);
        assertThat(page.items()).extracting(CommunityComplaintView::upvoteCount).containsExactly(3L, 1L, 0L);
        assertThat(ids(page)).doesNotContain(personalRoom, resolved, archived);
        assertThat(page.totalItems()).isEqualTo(3);
        assertThat(page.totalPages()).isEqualTo(1);
    }

    @Test
    void flagsWhatTheViewerReportedAndUpvoted() {
        List<CommunityComplaintView> items = communityComplaintService
                .getCommunityComplaints(VIEWER_EMAIL, null, "upvotes", 0, 20).items();

        assertThat(items).extracting(CommunityComplaintView::upvotedByYou).containsExactly(false, true, false);
        assertThat(items).extracting(CommunityComplaintView::reportedByYou).containsExactly(false, false, true);
    }

    @Test
    void includesComplaintDetails() {
        CommunityComplaintView first = communityComplaintService
                .getCommunityComplaints(VIEWER_EMAIL, null, "upvotes", 0, 20).items().getFirst();

        assertThat(first.title()).isEqualTo("Washroom not cleaned");
        assertThat(first.description()).isEqualTo("Community dashboard test");
        assertThat(first.status()).isEqualTo(Status.PENDING);
        assertThat(first.category()).isEqualTo("Cleaning");
        assertThat(first.floor()).isEqualTo("First");
        assertThat(first.wing()).isEqualTo("East wing washroom");
        assertThat(first.createdAt()).isNotNull();
    }

    @Test
    void sortsByMostRecent() {
        assertThat(ids(communityComplaintService.getCommunityComplaints(VIEWER_EMAIL, null, "recent", 0, 20)))
                .containsExactly(corridorLight, dispenser, washroom);
    }

    @Test
    void filtersByFloorAcceptingAnySpelling() {
        assertThat(ids(communityComplaintService.getCommunityComplaints(VIEWER_EMAIL, "1st floor", "upvotes", 0, 20)))
                .containsExactly(washroom, corridorLight);
        assertThat(ids(communityComplaintService.getCommunityComplaints(VIEWER_EMAIL, "Second", "recent", 0, 20)))
                .containsExactly(dispenser);
    }

    @Test
    void pagesThroughResults() {
        CommunityComplaintPage first = communityComplaintService.getCommunityComplaints(VIEWER_EMAIL, null, "upvotes", 0, 2);
        CommunityComplaintPage second = communityComplaintService.getCommunityComplaints(VIEWER_EMAIL, null, "upvotes", 1, 2);

        assertThat(ids(first)).containsExactly(washroom, dispenser);
        assertThat(ids(second)).containsExactly(corridorLight);
        assertThat(first.totalItems()).isEqualTo(3);
        assertThat(first.totalPages()).isEqualTo(2);
    }

    @Test
    void upvotingChangesTheOrderAndFlags() {
        // Two more upvotes put the corridor light ahead of the dispenser
        upvoteService.toggleUpvote(corridorLight, voters.get(0).getId());
        upvoteService.toggleUpvote(corridorLight, voters.get(1).getId());

        assertThat(ids(communityComplaintService.getCommunityComplaints(VIEWER_EMAIL, null, "upvotes", 0, 20)))
                .containsExactly(washroom, corridorLight, dispenser);
    }

    @Test
    void deletedComplaintsAreHidden() {
        jdbcTemplate.update("UPDATE complaints SET deleted_at = ?, deletion_reason = 'TEST_TICKET' WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.now()), dispenser);

        CommunityComplaintPage page = communityComplaintService.getCommunityComplaints(VIEWER_EMAIL, null, "upvotes", 0, 20);

        assertThat(ids(page)).containsExactly(washroom, corridorLight);
        assertThat(page.totalItems()).isEqualTo(2);
    }

    @Test
    void rejectsBadParameters() {
        assertStatus(() -> communityComplaintService.getCommunityComplaints(VIEWER_EMAIL, "Basement", "upvotes", 0, 20));
        assertStatus(() -> communityComplaintService.getCommunityComplaints(VIEWER_EMAIL, null, "popular", 0, 20));
        assertStatus(() -> communityComplaintService.getCommunityComplaints(VIEWER_EMAIL, null, "upvotes", -1, 20));
        assertStatus(() -> communityComplaintService.getCommunityComplaints(VIEWER_EMAIL, null, "upvotes", 0, 51));
    }

    private void assertStatus(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private Location communityArea(String floor, String wing) {
        Location location = new Location();
        location.setFloor(floor);
        location.setWing(wing);
        location.setCommunityArea(true);
        return locationRepository.save(location);
    }

    /** hoursAgo sets created_at directly, since @CreationTimestamp always writes "now". */
    private Long complaint(String title, User student, Status status, Location location, int hoursAgo) {
        Complaint complaint = new Complaint();
        complaint.setTitle(title);
        complaint.setDescription("Community dashboard test");
        complaint.setCategory(category);
        complaint.setLocation(location);
        complaint.setStudent(student);
        complaint.setStatus(status);
        Long id = complaintRepository.save(complaint).getId();
        jdbcTemplate.update("UPDATE complaints SET created_at = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusHours(hoursAgo)), id);
        return id;
    }

    private static List<Long> ids(CommunityComplaintPage page) {
        return page.items().stream().map(CommunityComplaintView::id).toList();
    }

    private static User user(String email) {
        User user = new User();
        user.setEmail(email);
        user.setName(email);
        user.setRole(Role.STUDENT);
        return user;
    }
}

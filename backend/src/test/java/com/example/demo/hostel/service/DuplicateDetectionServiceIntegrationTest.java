package com.example.demo.hostel.service;

import com.example.demo.hostel.dto.DuplicateCandidate;
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
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the pg_trgm similarity query against real PostgreSQL (H2 has no
 * pg_trgm). Skipped when Docker is unavailable; runs in CI.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.sql.init.mode=never"
})
class DuplicateDetectionServiceIntegrationTest {

    private static final String NEIGHBOUR_EMAIL = "neighbour@smail.iitm.ac.in";
    private static final String REPORTER_EMAIL = "reporter@smail.iitm.ac.in";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private DuplicateDetectionService duplicateDetectionService;

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

    private User reporter;
    private User neighbour;
    private Category category;
    private Location firstEast;
    private Location firstWest;

    private Long eastTapLeak;
    private Long westTapLeak;
    private Long dispenser;
    private Long otherFloorTapLeak;
    private Long personalRoomTapLeak;
    private Long resolvedTapLeak;
    private Long describedLeak;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm");

        reporter = userRepository.save(user(REPORTER_EMAIL));
        neighbour = userRepository.save(user(NEIGHBOUR_EMAIL));

        category = new Category();
        category.setName("Plumbing");
        category.setDefaultSlaHours(48);
        category = categoryRepository.save(category);

        firstEast = communityArea("First", "East wing washroom");
        firstWest = communityArea("First", "West wing washroom");
        Location secondEast = communityArea("Second", "East wing washroom");
        Location room204 = new Location();
        room204.setFloor("First");
        room204.setRoomNumber("204");
        room204 = locationRepository.save(room204);

        eastTapLeak = complaint("Washroom tap leaking", "Test fixture", Status.PENDING, firstEast);
        westTapLeak = complaint("Washroom tap leaking", "Test fixture", Status.ASSIGNED, firstWest);
        dispenser = complaint("Water dispenser not working", "Test fixture", Status.IN_PROGRESS, firstEast);
        otherFloorTapLeak = complaint("Washroom tap leaking", "Test fixture", Status.PENDING, secondEast);
        personalRoomTapLeak = complaint("Washroom tap leaking", "Test fixture", Status.PENDING, room204);
        resolvedTapLeak = complaint("Washroom tap leaking", "Test fixture", Status.RESOLVED, firstEast);
        describedLeak = complaint("Leak problem", "The tap in the washroom is leaking all night", Status.PENDING, firstEast);
    }

    @AfterEach
    void tearDown() {
        upvoteRepository.deleteAll();
        complaintRepository.deleteAll();
        categoryRepository.deleteAll();
        locationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void findsOnlyOpenCommunityComplaintsOnTheSameFloor() {
        List<Long> ids = ids(duplicateDetectionService.findPotentialDuplicates(
                NEIGHBOUR_EMAIL, "Washroom tap leaking", "First", "East wing washroom"));

        assertThat(ids).contains(eastTapLeak, westTapLeak).hasSizeLessThanOrEqualTo(3);
        assertThat(ids).doesNotContain(otherFloorTapLeak, personalRoomTapLeak, resolvedTapLeak, dispenser);
    }

    @Test
    void sameWingWinsATie() {
        assertThat(ids(duplicateDetectionService.findPotentialDuplicates(
                NEIGHBOUR_EMAIL, "Washroom tap leaking", "First", "East wing washroom")).getFirst())
                .isEqualTo(eastTapLeak);
        assertThat(ids(duplicateDetectionService.findPotentialDuplicates(
                NEIGHBOUR_EMAIL, "Washroom tap leaking", "First", "west wing washroom")).getFirst())
                .isEqualTo(westTapLeak);
    }

    @Test
    void matchesWordsInTheDescription() {
        List<Long> ids = ids(duplicateDetectionService.findPotentialDuplicates(
                NEIGHBOUR_EMAIL, "leaking all night", "First", null));

        assertThat(ids).first().isEqualTo(describedLeak);
    }

    @Test
    void unrelatedTitleFindsNothing() {
        assertThat(duplicateDetectionService.findPotentialDuplicates(
                NEIGHBOUR_EMAIL, "Broken window glass", "First", null)).isEmpty();
    }

    @Test
    void returnsAtMostThreeMatches() {
        complaint("Washroom tap leaking again", "Test fixture", Status.PENDING, firstEast);
        complaint("Washroom tap is leaking", "Test fixture", Status.PENDING, firstWest);

        assertThat(duplicateDetectionService.findPotentialDuplicates(
                NEIGHBOUR_EMAIL, "Washroom tap leaking", "First", null)).hasSize(3);
    }

    @Test
    void archivedComplaintsAreIgnored() {
        Complaint complaint = complaintRepository.findById(eastTapLeak).orElseThrow();
        complaint.setArchived(true);
        complaintRepository.save(complaint);

        assertThat(ids(duplicateDetectionService.findPotentialDuplicates(
                NEIGHBOUR_EMAIL, "Washroom tap leaking", "First", null))).doesNotContain(eastTapLeak);
    }

    @Test
    void reportsUpvoteCountAndWhetherTheCallerReportedOrUpvotedIt() {
        upvoteService.toggleUpvote(eastTapLeak, neighbour.getId());

        DuplicateCandidate forNeighbour = find(duplicateDetectionService.findPotentialDuplicates(
                NEIGHBOUR_EMAIL, "Washroom tap leaking", "First", "East wing washroom"), eastTapLeak);
        assertThat(forNeighbour.upvoteCount()).isEqualTo(1);
        assertThat(forNeighbour.upvotedByYou()).isTrue();
        assertThat(forNeighbour.reportedByYou()).isFalse();
        assertThat(forNeighbour.floor()).isEqualTo("First");
        assertThat(forNeighbour.wing()).isEqualTo("East wing washroom");

        DuplicateCandidate forReporter = find(duplicateDetectionService.findPotentialDuplicates(
                REPORTER_EMAIL, "Washroom tap leaking", "First", "East wing washroom"), eastTapLeak);
        assertThat(forReporter.reportedByYou()).isTrue();
        assertThat(forReporter.upvotedByYou()).isFalse();
    }

    private Location communityArea(String floor, String wing) {
        Location location = new Location();
        location.setFloor(floor);
        location.setWing(wing);
        location.setCommunityArea(true);
        return locationRepository.save(location);
    }

    private Long complaint(String title, String description, Status status, Location location) {
        Complaint complaint = new Complaint();
        complaint.setTitle(title);
        complaint.setDescription(description);
        complaint.setCategory(category);
        complaint.setLocation(location);
        complaint.setStudent(reporter);
        complaint.setStatus(status);
        return complaintRepository.save(complaint).getId();
    }

    private static DuplicateCandidate find(List<DuplicateCandidate> candidates, Long id) {
        return candidates.stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
    }

    private static List<Long> ids(List<DuplicateCandidate> candidates) {
        return candidates.stream().map(DuplicateCandidate::id).toList();
    }

    private static User user(String email) {
        User user = new User();
        user.setEmail(email);
        user.setName(email);
        user.setRole(Role.STUDENT);
        return user;
    }
}

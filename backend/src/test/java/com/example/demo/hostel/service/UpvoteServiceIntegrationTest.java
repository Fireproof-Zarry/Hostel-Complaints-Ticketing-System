package com.example.demo.hostel.service;

import com.example.demo.hostel.model.Category;
import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.model.Location;
import com.example.demo.hostel.model.Role;
import com.example.demo.hostel.model.Status;
import com.example.demo.hostel.model.Upvote;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:upvote-test",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class UpvoteServiceIntegrationTest {

    @Autowired
    private UpvoteService upvoteService;

    @Autowired
    private UpvoteRepository upvoteRepository;

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

    private User reporter;
    private User neighbour;
    private User otherNeighbour;
    private User admin;
    private Long communityComplaint;
    private Long personalComplaint;

    @BeforeEach
    void setUp() {
        reporter = userRepository.save(user("reporter@smail.iitm.ac.in", Role.STUDENT));
        neighbour = userRepository.save(user("neighbour@smail.iitm.ac.in", Role.STUDENT));
        otherNeighbour = userRepository.save(user("other@smail.iitm.ac.in", Role.STUDENT));
        admin = userRepository.save(user("admin@smail.iitm.ac.in", Role.ADMIN));

        Category category = new Category();
        category.setName("Cleaning");
        category.setDefaultSlaHours(24);
        category = categoryRepository.save(category);

        Location washroom = new Location();
        washroom.setFloor("First");
        washroom.setWing("East wing washroom");
        washroom.setCommunityArea(true);
        washroom = locationRepository.save(washroom);

        Location room = new Location();
        room.setFloor("First");
        room.setRoomNumber("204");
        room = locationRepository.save(room);

        communityComplaint = complaint(category, washroom);
        personalComplaint = complaint(category, room);
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
    void togglingAddsThenRemovesTheUpvote() {
        assertThat(upvoteService.toggleUpvote(communityComplaint, neighbour.getId())).isTrue();
        assertThat(upvoteService.countUpvotes(communityComplaint)).isEqualTo(1);

        assertThat(upvoteService.toggleUpvote(communityComplaint, neighbour.getId())).isFalse();
        assertThat(upvoteService.countUpvotes(communityComplaint)).isZero();

        assertThat(upvoteService.toggleUpvote(communityComplaint, neighbour.getId())).isTrue();
        assertThat(upvoteService.countUpvotes(communityComplaint)).isEqualTo(1);
    }

    @Test
    void countsUpvotesFromDifferentStudents() {
        upvoteService.toggleUpvote(communityComplaint, neighbour.getId());
        upvoteService.toggleUpvote(communityComplaint, otherNeighbour.getId());

        assertThat(upvoteService.countUpvotes(communityComplaint)).isEqualTo(2);
    }

    @Test
    void cannotUpvoteOwnComplaint() {
        assertStatus(() -> upvoteService.toggleUpvote(communityComplaint, reporter.getId()), HttpStatus.BAD_REQUEST);
    }

    @Test
    void personalRoomComplaintsAreNotFound() {
        assertStatus(() -> upvoteService.toggleUpvote(personalComplaint, neighbour.getId()), HttpStatus.NOT_FOUND);
    }

    @Test
    void archivedComplaintsAreNotFound() {
        Complaint complaint = complaintRepository.findById(communityComplaint).orElseThrow();
        complaint.setStatus(Status.RESOLVED);
        complaint.setArchived(true);
        complaintRepository.save(complaint);

        assertStatus(() -> upvoteService.toggleUpvote(communityComplaint, neighbour.getId()), HttpStatus.NOT_FOUND);
    }

    @Test
    void missingComplaintIsNotFound() {
        assertStatus(() -> upvoteService.toggleUpvote(999_999L, neighbour.getId()), HttpStatus.NOT_FOUND);
    }

    @Test
    void adminsCannotUpvote() {
        assertStatus(() -> upvoteService.toggleUpvote(communityComplaint, admin.getId()), HttpStatus.FORBIDDEN);
    }

    @Test
    void compositePrimaryKeyRejectsDuplicateUpvotes() {
        upvoteService.toggleUpvote(communityComplaint, neighbour.getId());

        // What a simultaneous second insert would hit
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(tx -> {
            Complaint complaint = complaintRepository.findById(communityComplaint).orElseThrow();
            User user = userRepository.findById(neighbour.getId()).orElseThrow();
            upvoteRepository.saveAndFlush(new Upvote(complaint, user));
        })).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(upvoteService.countUpvotes(communityComplaint)).isEqualTo(1);
    }

    @Test
    void deletingComplaintRemovesItsUpvotes() {
        upvoteService.toggleUpvote(communityComplaint, neighbour.getId());

        complaintRepository.deleteById(communityComplaint);

        assertThat(upvoteRepository.count()).isZero();
    }

    private Long complaint(Category category, Location location) {
        Complaint complaint = new Complaint();
        complaint.setTitle("Dirty washroom");
        complaint.setDescription("Not cleaned today");
        complaint.setCategory(category);
        complaint.setLocation(location);
        complaint.setStudent(reporter);
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

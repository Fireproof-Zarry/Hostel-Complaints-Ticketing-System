package com.example.demo.hostel.service;

import com.example.demo.hostel.model.Category;
import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.model.Location;
import com.example.demo.hostel.model.Role;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:complaint-purge-test",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class ComplaintPurgeServiceIntegrationTest {

    private static final String IMAGE = "https://project.supabase.co/storage/v1/object/public/complaint-images/a.jpg";
    private static final String BROKEN_IMAGE = "https://project.supabase.co/storage/v1/object/public/complaint-images/b.jpg";

    @Autowired private ComplaintPurgeService purgeService;
    @Autowired private UpvoteService upvoteService;
    @Autowired private ComplaintTransitionService transitionService;
    @Autowired private ComplaintRepository complaintRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private SupabaseStorageService storageService;

    private User owner;
    private User neighbour;
    private User admin;
    private Category category;
    private Location washroom;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(user("owner@smail.iitm.ac.in", Role.STUDENT));
        neighbour = userRepository.save(user("neighbour@smail.iitm.ac.in", Role.STUDENT));
        admin = userRepository.save(user("admin@smail.iitm.ac.in", Role.ADMIN));

        category = new Category();
        category.setName("Cleaning");
        category.setDefaultSlaHours(24);
        category = categoryRepository.save(category);

        washroom = new Location();
        washroom.setFloor("First");
        washroom.setWing("East wing washroom");
        washroom.setCommunityArea(true);
        washroom = locationRepository.save(washroom);

        when(storageService.deleteImage(any())).thenReturn(true);
        when(storageService.deleteImage(BROKEN_IMAGE)).thenReturn(false);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM upvotes");
        jdbcTemplate.update("DELETE FROM complaint_history");
        jdbcTemplate.update("DELETE FROM complaints");
        categoryRepository.deleteAll();
        locationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void hardDeletesComplaintsDeletedMoreThan15DaysAgoWithTheirPhotoHistoryAndUpvotes() {
        Long old = complaint(IMAGE);
        upvoteService.toggleUpvote(old, neighbour.getId());
        // Reopened before deletion, so it has history (which has no ON DELETE CASCADE)
        transitionService.changeComplaintStatus(old, admin.getId(), "IN_PROGRESS", null);
        transitionService.changeComplaintStatus(old, admin.getId(), "PENDING", null);
        softDelete(old, 16);

        assertThat(purgeService.purgeDeletedComplaints()).isEqualTo(1);

        assertThat(count("complaints", old)).isZero();
        assertThat(count("complaint_history", old)).isZero();
        assertThat(count("upvotes", old)).isZero();
        verify(storageService).deleteImage(IMAGE);
    }

    @Test
    void keepsRecentlyDeletedAndActiveComplaints() {
        Long recentlyDeleted = complaint(null);
        softDelete(recentlyDeleted, 14);
        Long active = complaint(null);

        assertThat(purgeService.purgeDeletedComplaints()).isZero();

        assertThat(count("complaints", recentlyDeleted)).isEqualTo(1);
        assertThat(count("complaints", active)).isEqualTo(1);
    }

    @Test
    void keepsTheRowWhenItsPhotoCannotBeDeletedSoTheNextRunRetries() {
        Long stuck = complaint(BROKEN_IMAGE);
        softDelete(stuck, 20);
        Long purgeable = complaint(IMAGE);
        softDelete(purgeable, 20);

        assertThat(purgeService.purgeDeletedComplaints()).isEqualTo(1);

        assertThat(count("complaints", stuck)).isEqualTo(1);
        assertThat(count("complaints", purgeable)).isZero();

        // Storage recovers: the next run purges it
        when(storageService.deleteImage(BROKEN_IMAGE)).thenReturn(true);
        assertThat(purgeService.purgeDeletedComplaints()).isEqualTo(1);
        assertThat(count("complaints", stuck)).isZero();
    }

    @Test
    void nothingToPurgeTouchesNoStorage() {
        complaint(IMAGE);

        assertThat(purgeService.purgeDeletedComplaints()).isZero();
        verify(storageService, never()).deleteImage(any());
    }

    @Test
    void runsDailyAt3amIndiaTime() throws Exception {
        Scheduled scheduled = ComplaintPurgeService.class
                .getMethod("purgeDeletedComplaints")
                .getAnnotation(Scheduled.class);

        assertThat(scheduled.cron()).isEqualTo("0 0 3 * * ?");
        assertThat(scheduled.zone()).isEqualTo("Asia/Kolkata");
        assertThat(ComplaintPurgeService.RETENTION_DAYS).isEqualTo(15);
    }

    private Long complaint(String imageUrl) {
        Complaint complaint = new Complaint();
        complaint.setTitle("Dirty washroom");
        complaint.setDescription("Purge test");
        complaint.setCategory(category);
        complaint.setLocation(washroom);
        complaint.setStudent(owner);
        complaint.setImageUrl(imageUrl);
        return complaintRepository.save(complaint).getId();
    }

    private void softDelete(Long id, int daysAgo) {
        jdbcTemplate.update("UPDATE complaints SET deleted_at = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusDays(daysAgo)), id);
    }

    private int count(String table, Long complaintId) {
        String column = table.equals("complaints") ? "id" : "complaint_id";
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?", Integer.class, complaintId);
    }

    private static User user(String email, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setName(email);
        user.setRole(role);
        return user;
    }
}

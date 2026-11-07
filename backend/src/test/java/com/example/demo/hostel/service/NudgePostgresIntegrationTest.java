package com.example.demo.hostel.service;

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
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The nudge feature end to end on real PostgreSQL: schema_nov07.sql, the API, and
 * the conditional UPDATE, whose handling of simultaneous nudges depends on
 * PostgreSQL's row locks. Skipped when Docker is unavailable; runs in CI.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.sql.init.mode=never"
})
@AutoConfigureMockMvc
class NudgePostgresIntegrationTest {

    private static final String OWNER_EMAIL = "owner@smail.iitm.ac.in";
    private static final String OTHER_EMAIL = "other@smail.iitm.ac.in";
    private static final List<Status> UNRESOLVED = List.of(Status.PENDING, Status.ASSIGNED, Status.IN_PROGRESS);
    private static final int SIMULTANEOUS_NUDGES = 8;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockitoBean private FcmNotificationService fcmNotificationService;

    @Autowired private NudgeService nudgeService;
    @Autowired private AdminService adminService;
    @Autowired private MockMvc mockMvc;
    @Autowired private ComplaintRepository complaintRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;
    @Autowired private PlatformTransactionManager transactionManager;

    private User owner;
    private Category category;
    private Location room;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(user(OWNER_EMAIL, Role.STUDENT, "owner-token"));
        userRepository.save(user(OTHER_EMAIL, Role.STUDENT, "other-token"));
        userRepository.save(user("admin1@smail.iitm.ac.in", Role.ADMIN, "admin-token-1"));
        userRepository.save(user("admin2@smail.iitm.ac.in", Role.ADMIN, "admin-token-2"));
        userRepository.save(user("admin3@smail.iitm.ac.in", Role.ADMIN, null));
        userRepository.save(user("staff@smail.iitm.ac.in", Role.STAFF, "staff-token"));

        Category newCategory = new Category();
        newCategory.setName("Plumbing");
        newCategory.setDefaultSlaHours(48);
        category = categoryRepository.save(newCategory);

        Location newLocation = new Location();
        newLocation.setFloor("Second");
        newLocation.setRoomNumber("204");
        room = locationRepository.save(newLocation);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM complaints");
        categoryRepository.deleteAll();
        locationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void migrationAddsTheColumnToAnOldDatabaseAndCanBeRerun() throws Exception {
        Long existing = complaintCreatedAt(LocalDateTime.now().minusDays(8), Status.PENDING);
        jdbcTemplate.execute("ALTER TABLE complaints DROP COLUMN last_nudged_at");

        migrate();
        migrate();

        Map<String, Object> column = jdbcTemplate.queryForMap("""
                SELECT data_type, datetime_precision, is_nullable
                FROM information_schema.columns
                WHERE table_name = 'complaints' AND column_name = 'last_nudged_at'
                """);
        assertThat(column).containsEntry("data_type", "timestamp without time zone")
                .containsEntry("datetime_precision", 6)
                .containsEntry("is_nullable", "YES");

        nudgeOverHttp(existing).andExpect(status().isOk());
        assertThat(lastNudgedAt(existing)).isNotNull();
    }

    @Test
    void tooEarlyNudgeIsRejectedThenAnOlderUnresolvedComplaintNotifiesAdminsOnce() throws Exception {
        Long recent = complaintCreatedAt(LocalDateTime.now().minusDays(6), Status.PENDING);
        Long old = complaintCreatedAt(LocalDateTime.now().minusDays(8), Status.IN_PROGRESS);

        nudgeOverHttp(recent).andExpect(status().isConflict());
        verify(fcmNotificationService, never()).sendNudgeNotification(anyList(), any(), any());
        assertThat(lastNudgedAt(recent)).isNull();

        nudgeOverHttp(old).andExpect(status().isOk());

        // Admins with a token only: not staff, students, or an admin without one
        verify(fcmNotificationService, times(1)).sendNudgeNotification(
                argThat(tokens -> tokens.size() == 2
                        && tokens.containsAll(List.of("admin-token-1", "admin-token-2"))),
                eq(old),
                eq("Leaking tap"));
    }

    @Test
    void ofSimultaneousNudgesOnlyOneSucceedsAndNotifies() throws Exception {
        Long complaint = complaintCreatedAt(LocalDateTime.now().minusDays(8), Status.PENDING);

        List<HttpStatusCode> results = nudgeAtTheSameMoment(complaint);

        assertThat(Collections.frequency(results, HttpStatus.OK)).isEqualTo(1);
        assertThat(Collections.frequency(results, HttpStatus.CONFLICT)).isEqualTo(SIMULTANEOUS_NUDGES - 1);
        assertThat(lastNudgedAt(complaint)).isNotNull();
        verify(fcmNotificationService, times(1)).sendNudgeNotification(anyList(), any(), any());
    }

    @Test
    void aRolledBackNudgeSavesNothingAndNotifiesNobody() {
        Long complaint = complaintCreatedAt(LocalDateTime.now().minusDays(8), Status.PENDING);

        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            nudgeService.nudge(complaint, OWNER_EMAIL);
            transaction.setRollbackOnly();
        });

        assertThat(lastNudgedAt(complaint)).isNull();
        verify(fcmNotificationService, never()).sendNudgeNotification(anyList(), any(), any());
    }

    @Test
    void repeatWithinSevenDaysIsRejectedAndSendsNothingMore() throws Exception {
        Long complaint = complaintCreatedAt(LocalDateTime.now().minusDays(20), Status.ASSIGNED);

        nudgeOverHttp(complaint).andExpect(status().isOk());
        nudgeOverHttp(complaint).andExpect(status().isConflict());

        verify(fcmNotificationService, times(1)).sendNudgeNotification(anyList(), any(), any());
    }

    @Test
    void canNudgeAgainOnceTheLastNudgeIsOverSevenDaysOld() throws Exception {
        Long complaint = complaintCreatedAt(LocalDateTime.now().minusDays(20), Status.IN_PROGRESS);
        setLastNudgedAt(complaint, LocalDateTime.now().minusDays(8));

        nudgeOverHttp(complaint).andExpect(status().isOk());
    }

    @Test
    void closedComplaintsAreRejected() throws Exception {
        nudgeOverHttp(complaintCreatedAt(LocalDateTime.now().minusDays(20), Status.RESOLVED))
                .andExpect(status().isConflict());
        nudgeOverHttp(complaintCreatedAt(LocalDateTime.now().minusDays(20), Status.REJECTED))
                .andExpect(status().isConflict());

        verify(fcmNotificationService, never()).sendNudgeNotification(anyList(), any(), any());
    }

    @Test
    void missingDeletedOrSomeoneElsesComplaintIsNotFound() throws Exception {
        Long someoneElses = complaintCreatedAt(LocalDateTime.now().minusDays(8), Status.PENDING);
        Long deleted = complaintCreatedAt(LocalDateTime.now().minusDays(8), Status.PENDING);
        jdbcTemplate.update("UPDATE complaints SET deleted_at = NOW() WHERE id = ?", deleted);

        nudgeOverHttp(999_999L).andExpect(status().isNotFound());
        nudgeOverHttp(deleted).andExpect(status().isNotFound());
        nudgeOverHttp(someoneElses, OTHER_EMAIL).andExpect(status().isNotFound());

        verify(fcmNotificationService, never()).sendNudgeNotification(anyList(), any(), any());
    }

    @Test
    void myComplaintsTellTheOwnerWhetherTheyCanNudge() throws Exception {
        Long recent = complaintCreatedAt(LocalDateTime.now().minusDays(2), Status.PENDING);
        Long old = complaintCreatedAt(LocalDateTime.now().minusDays(9), Status.PENDING);
        Long closed = complaintCreatedAt(LocalDateTime.now().minusDays(9), Status.RESOLVED);

        mockMvc.perform(get("/api/complaints/mine").with(signedInAs(OWNER_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == %d)].canNudge".formatted(recent)).value(false))
                .andExpect(jsonPath("$[?(@.id == %d)].nextNudgeAt".formatted(recent)).isNotEmpty())
                .andExpect(jsonPath("$[?(@.id == %d)].canNudge".formatted(old)).value(true))
                .andExpect(jsonPath("$[?(@.id == %d)].canNudge".formatted(closed)).value(false));
    }

    @Test
    void adminListPutsUnresolvedNudgedComplaintsFirstLatestNudgeOnTop() {
        Long plain = complaintCreatedAt(LocalDateTime.now().minusDays(30), Status.PENDING);
        Long nudgedEarlier = complaintCreatedAt(LocalDateTime.now().minusDays(20), Status.PENDING);
        Long nudgedLater = complaintCreatedAt(LocalDateTime.now().minusDays(10), Status.IN_PROGRESS);
        Long nudgedThenResolved = complaintCreatedAt(LocalDateTime.now().minusDays(25), Status.RESOLVED);
        setLastNudgedAt(nudgedEarlier, LocalDateTime.now().minusDays(2));
        setLastNudgedAt(nudgedLater, LocalDateTime.now().minusDays(1));
        setLastNudgedAt(nudgedThenResolved, LocalDateTime.now());

        List<Long> ids = adminService.getComplaints(null, null, null, null, null, null, null).stream()
                .map(Complaint::getId)
                .toList();

        assertThat(ids.subList(0, 2)).containsExactly(nudgedLater, nudgedEarlier);
        assertThat(ids).containsExactlyInAnyOrder(plain, nudgedEarlier, nudgedLater, nudgedThenResolved);
    }

    // The service checks first, so these reach the UPDATE directly: it is what
    // holds if the complaint changes between that check and the write
    @Test
    void conditionalUpdateAllowsOnlyAnEligibleComplaint() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        LocalDateTime cutoff = NudgeService.cutoff(now, java.time.ZoneId.systemDefault());

        Long eligible = complaintCreatedAt(cutoff.minusNanos(1_000), Status.ASSIGNED);
        Long nudgedLongAgo = complaintCreatedAt(cutoff.minusDays(10), Status.PENDING);
        setLastNudgedAt(nudgedLongAgo, cutoff.minusNanos(1_000));

        // Filed or nudged at the start of the first day that has not opened yet
        Long filedOnTheNextDay = complaintCreatedAt(cutoff, Status.PENDING);
        Long nudgedOnTheNextDay = complaintCreatedAt(cutoff.minusDays(10), Status.PENDING);
        setLastNudgedAt(nudgedOnTheNextDay, cutoff);
        Long resolved = complaintCreatedAt(cutoff.minusDays(10), Status.RESOLVED);
        Long rejected = complaintCreatedAt(cutoff.minusDays(10), Status.REJECTED);

        assertThat(recordNudge(eligible, now, cutoff)).isEqualTo(1);
        assertThat(recordNudge(nudgedLongAgo, now, cutoff)).isEqualTo(1);
        assertThat(recordNudge(filedOnTheNextDay, now, cutoff)).isZero();
        assertThat(recordNudge(nudgedOnTheNextDay, now, cutoff)).isZero();
        assertThat(recordNudge(resolved, now, cutoff)).isZero();
        assertThat(recordNudge(rejected, now, cutoff)).isZero();
    }

    @Test
    void nudgeKeepsVersionAndUpdatedAtAndSurvivesASaveOfAnOlderCopy() {
        Long id = complaintCreatedAt(LocalDateTime.now().minusDays(8), Status.PENDING);
        Complaint loadedBeforeNudge = complaintRepository.findById(id).orElseThrow();

        nudgeService.nudge(id, OWNER_EMAIL);

        Complaint nudged = complaintRepository.findById(id).orElseThrow();
        assertThat(nudged.getVersion()).isEqualTo(loadedBeforeNudge.getVersion());
        assertThat(nudged.getUpdatedAt()).isEqualTo(loadedBeforeNudge.getUpdatedAt());

        loadedBeforeNudge.setAssignedTo("Warden");
        complaintRepository.save(loadedBeforeNudge);

        Complaint afterSave = complaintRepository.findById(id).orElseThrow();
        assertThat(afterSave.getAssignedTo()).isEqualTo("Warden");
        assertThat(afterSave.getLastNudgedAt()).isEqualTo(nudged.getLastNudgedAt());
    }

    private List<HttpStatusCode> nudgeAtTheSameMoment(Long complaint) throws Exception {
        CountDownLatch ready = new CountDownLatch(SIMULTANEOUS_NUDGES);
        CountDownLatch start = new CountDownLatch(1);
        Callable<HttpStatusCode> nudge = () -> {
            ready.countDown();
            start.await();
            try {
                nudgeService.nudge(complaint, OWNER_EMAIL);
                return HttpStatus.OK;
            } catch (ResponseStatusException e) {
                return e.getStatusCode();
            }
        };

        ExecutorService executor = Executors.newFixedThreadPool(SIMULTANEOUS_NUDGES);
        try {
            List<Future<HttpStatusCode>> futures = new ArrayList<>();
            for (int i = 0; i < SIMULTANEOUS_NUDGES; i++) {
                futures.add(executor.submit(nudge));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<HttpStatusCode> results = new ArrayList<>();
            for (Future<HttpStatusCode> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private ResultActions nudgeOverHttp(Long complaint) throws Exception {
        return nudgeOverHttp(complaint, OWNER_EMAIL);
    }

    private ResultActions nudgeOverHttp(Long complaint, String email) throws Exception {
        return mockMvc.perform(post("/api/complaints/{id}/nudge", complaint).with(signedInAs(email)));
    }

    private static RequestPostProcessor signedInAs(String email) {
        return jwt().jwt(token -> token.claim("email", email));
    }

    private int recordNudge(Long complaint, LocalDateTime now, LocalDateTime cutoff) {
        return new TransactionTemplate(transactionManager).execute(
                transaction -> complaintRepository.recordNudge(complaint, UNRESOLVED, now, cutoff));
    }

    private void migrate() {
        new ResourceDatabasePopulator(new ClassPathResource("schema_nov07.sql")).execute(dataSource);
    }

    private LocalDateTime lastNudgedAt(Long complaint) {
        return jdbcTemplate.queryForObject(
                "SELECT last_nudged_at FROM complaints WHERE id = ?", LocalDateTime.class, complaint);
    }

    private void setLastNudgedAt(Long complaint, LocalDateTime when) {
        jdbcTemplate.update("UPDATE complaints SET last_nudged_at = ? WHERE id = ?",
                Timestamp.valueOf(when), complaint);
    }

    private Long complaintCreatedAt(LocalDateTime createdAt, Status status) {
        Complaint complaint = new Complaint();
        complaint.setTitle("Leaking tap");
        complaint.setDescription("Water everywhere");
        complaint.setCategory(category);
        complaint.setLocation(room);
        complaint.setStudent(owner);
        complaint.setStatus(status);
        Long id = complaintRepository.save(complaint).getId();
        // created_at is set by Hibernate and not updatable, so age it directly
        jdbcTemplate.update("UPDATE complaints SET created_at = ? WHERE id = ?", Timestamp.valueOf(createdAt), id);
        return id;
    }

    private static User user(String email, Role role, String fcmToken) {
        User user = new User();
        user.setEmail(email);
        user.setName(email);
        user.setRole(role);
        user.setFcmToken(fcmToken);
        return user;
    }
}

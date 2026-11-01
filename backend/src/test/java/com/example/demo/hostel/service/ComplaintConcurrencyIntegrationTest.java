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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Optimistic locking (@Version) on Complaint: a stale update fails instead of overwriting. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:complaint-concurrency-test",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class ComplaintConcurrencyIntegrationTest {

    @Autowired private ComplaintRepository complaintRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private ComplaintArchivalService archivalService;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Long complaintId;

    @BeforeEach
    void setUp() {
        User student = new User();
        student.setEmail("student@smail.iitm.ac.in");
        student.setName("Student");
        student.setRole(Role.STUDENT);
        student = userRepository.save(student);

        Category category = new Category();
        category.setName("Electrical");
        category.setDefaultSlaHours(48);

        Location location = new Location();
        location.setFloor("First");
        location.setRoomNumber("204");

        Complaint complaint = new Complaint();
        complaint.setTitle("Fan not working");
        complaint.setDescription("Concurrency test");
        complaint.setCategory(categoryRepository.save(category));
        complaint.setLocation(locationRepository.save(location));
        complaint.setStudent(student);
        complaintId = complaintRepository.save(complaint).getId();
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM complaints");
        categoryRepository.deleteAll();
        locationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void newComplaintStartsAtVersionZero() {
        assertThat(complaintRepository.findById(complaintId).orElseThrow().getVersion()).isZero();
    }

    @Test
    void secondSaveOfTheSameVersionFails() {
        // Two separate transactions each load their own copy of version 0
        Complaint adminA = transactionTemplate.execute(tx -> complaintRepository.findById(complaintId).orElseThrow());
        Complaint adminB = transactionTemplate.execute(tx -> complaintRepository.findById(complaintId).orElseThrow());
        assertThat(adminA.getVersion()).isEqualTo(adminB.getVersion()).isZero();

        adminA.setStatus(Status.IN_PROGRESS);
        adminB.setStatus(Status.REJECTED);

        complaintRepository.save(adminA);
        assertThatThrownBy(() -> complaintRepository.save(adminB))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        // The first change was kept, not overwritten by the stale one
        Complaint stored = complaintRepository.findById(complaintId).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(Status.IN_PROGRESS);
        assertThat(stored.getVersion()).isEqualTo(1L);
    }

    @Test
    void concurrentTransactionsInTwoThreadsLetOnlyOneCommit() throws Exception {
        CountDownLatch bothLoaded = new CountDownLatch(2);
        CountDownLatch firstCommitted = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = pool.submit(() -> transactionTemplate.executeWithoutResult(tx -> {
                Complaint complaint = complaintRepository.findById(complaintId).orElseThrow();
                bothLoaded.countDown();
                await(bothLoaded);
                complaint.setAssignedTo("warden-a@smail.iitm.ac.in");
            }));
            Future<?> second = pool.submit(() -> transactionTemplate.executeWithoutResult(tx -> {
                Complaint complaint = complaintRepository.findById(complaintId).orElseThrow();
                bothLoaded.countDown();
                await(bothLoaded);
                // Commit only after the other thread's transaction has committed
                await(firstCommitted);
                complaint.setAssignedTo("warden-b@smail.iitm.ac.in");
            }));

            first.get(10, TimeUnit.SECONDS);
            firstCommitted.countDown();

            assertThatThrownBy(() -> second.get(10, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(ObjectOptimisticLockingFailureException.class);
        } finally {
            pool.shutdownNow();
        }

        Complaint stored = complaintRepository.findById(complaintId).orElseThrow();
        assertThat(stored.getAssignedTo()).isEqualTo("warden-a@smail.iitm.ac.in");
        assertThat(stored.getVersion()).isEqualTo(1L);
    }

    @Test
    void editBasedOnACopyFromBeforeTheArchiveJobConflicts() {
        Complaint complaint = complaintRepository.findById(complaintId).orElseThrow();
        complaint.setStatus(Status.RESOLVED);
        complaintRepository.save(complaint);
        jdbcTemplate.update("UPDATE complaints SET updated_at = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusDays(40)), complaintId);

        // An admin loads it, then the nightly job archives it (a bulk UPDATE)
        Complaint staleCopy = transactionTemplate.execute(tx -> complaintRepository.findById(complaintId).orElseThrow());
        assertThat(archivalService.archiveOldComplaints()).isEqualTo(1);

        // Saving the pre-archive copy would silently un-archive it; it conflicts instead
        staleCopy.setAssignedTo("warden@smail.iitm.ac.in");
        assertThatThrownBy(() -> complaintRepository.save(staleCopy))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(complaintRepository.findById(complaintId).orElseThrow().isArchived()).isTrue();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for the other transaction");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}

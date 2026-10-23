package com.example.demo.hostel.service;

import com.example.demo.hostel.model.Category;
import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.model.ComplaintHistory;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:complaint-transition-test",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class ComplaintTransitionServiceIntegrationTest {

    @Autowired
    private ComplaintTransitionService transitionService;

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

    // Spy so the rollback test can make the history insert fail after the
    // complaint update has already been issued inside the transaction.
    @MockitoSpyBean
    private ComplaintHistoryRepository complaintHistoryRepository;

    @MockitoBean
    private FcmNotificationService fcmNotificationService;

    private Long complaintId;
    private String adminId;
    private String studentId;

    @BeforeEach
    void setUp() {
        User student = user("student@smail.iitm.ac.in", Role.STUDENT);
        student.setFcmToken("student-device-token");
        student = userRepository.save(student);
        studentId = student.getId();
        adminId = userRepository.save(user("admin@smail.iitm.ac.in", Role.ADMIN)).getId();

        Category category = new Category();
        category.setName("Plumbing");
        category.setDefaultSlaHours(48);

        Location location = new Location();
        location.setFloor("First");
        location.setRoomNumber("204");

        Complaint complaint = new Complaint();
        complaint.setTitle("Leaking tap");
        complaint.setDescription("Dripping all night");
        complaint.setCategory(categoryRepository.save(category));
        complaint.setLocation(locationRepository.save(location));
        complaint.setStudent(student);
        complaintId = complaintRepository.save(complaint).getId();
    }

    @AfterEach
    void tearDown() {
        complaintHistoryRepository.deleteAll();
        complaintRepository.deleteAll();
        categoryRepository.deleteAll();
        locationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void changeComplaintStatus_savesComplaintAndHistory() {
        transitionService.changeComplaintStatus(complaintId, adminId, "in_progress", " Plumber assigned ");

        Complaint complaint = complaintRepository.findById(complaintId).orElseThrow();
        assertThat(complaint.getStatus()).isEqualTo(Status.IN_PROGRESS);

        // History associations are lazy, so read them inside a transaction
        transactionTemplate.executeWithoutResult(tx -> {
            List<ComplaintHistory> history = complaintHistoryRepository.findByComplaintOrderByChangedAtAsc(complaint);
            assertThat(history).hasSize(1);

            ComplaintHistory entry = history.getFirst();
            assertThat(entry.getComplaint().getId()).isEqualTo(complaintId);
            assertThat(entry.getActor().getId()).isEqualTo(adminId);
            assertThat(entry.getOldStatus()).isEqualTo(Status.PENDING);
            assertThat(entry.getNewStatus()).isEqualTo(Status.IN_PROGRESS);
            assertThat(entry.getReason()).isEqualTo("Plumber assigned");
            assertThat(entry.getChangedAt()).isNotNull();
        });

        // Sent after commit, on the async executor
        verify(fcmNotificationService, timeout(2000))
                .sendStatusUpdateNotification("student-device-token", "Leaking tap", "IN_PROGRESS");
    }

    @Test
    void changeComplaintStatus_rollsBackComplaintWhenHistorySaveFails() {
        doThrow(new RuntimeException("simulated history failure"))
                .when(complaintHistoryRepository).save(any(ComplaintHistory.class));

        assertThatThrownBy(() ->
                transitionService.changeComplaintStatus(complaintId, adminId, "RESOLVED", "Fixed")
        ).hasMessageContaining("simulated history failure");

        // The failure happened after the complaint was updated and saved
        verify(complaintHistoryRepository).save(any(ComplaintHistory.class));

        Complaint complaint = complaintRepository.findById(complaintId).orElseThrow();
        assertThat(complaint.getStatus()).isEqualTo(Status.PENDING);
        assertThat(complaintHistoryRepository.count()).isZero();

        // A rolled-back change must never notify the student
        verify(fcmNotificationService, never())
                .sendStatusUpdateNotification(anyString(), anyString(), anyString());
    }

    @Test
    void changeComplaintStatus_notifiesOnlyAfterCommit() {
        // The status change completes, but the surrounding transaction rolls back
        transactionTemplate.executeWithoutResult(tx -> {
            transitionService.changeComplaintStatus(complaintId, adminId, "RESOLVED", null);
            verify(fcmNotificationService, never())
                    .sendStatusUpdateNotification(anyString(), anyString(), anyString());
            tx.setRollbackOnly();
        });

        assertThat(complaintRepository.findById(complaintId).orElseThrow().getStatus())
                .isEqualTo(Status.PENDING);
        verify(fcmNotificationService, never())
                .sendStatusUpdateNotification(anyString(), anyString(), anyString());
    }

    @Test
    void changeComplaintStatus_withoutDeviceToken_doesNotNotify() {
        User student = userRepository.findById(studentId).orElseThrow();
        student.setFcmToken(null);
        userRepository.save(student);

        transitionService.changeComplaintStatus(complaintId, adminId, "RESOLVED", null);

        assertThat(complaintRepository.findById(complaintId).orElseThrow().getStatus())
                .isEqualTo(Status.RESOLVED);
        verify(fcmNotificationService, never())
                .sendStatusUpdateNotification(anyString(), anyString(), anyString());
    }

    private User user(String email, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setName(email);
        user.setRole(role);
        return user;
    }
}

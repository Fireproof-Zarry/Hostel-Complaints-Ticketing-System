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
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.sql.init.mode=never"
})
class ComplaintHistoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ComplaintService complaintService;

    @Autowired
    private ComplaintHistoryRepository historyRepository;

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

    @MockitoBean
    private SupabaseStorageService storageService;

    private Complaint complaint;
    private User student;
    private User admin;

    @BeforeEach
    void setUp() {
        student = userRepository.save(user("student@smail.iitm.ac.in", Role.STUDENT));
        admin = userRepository.save(user("admin@smail.iitm.ac.in", Role.ADMIN));

        Category category = new Category();
        category.setName("Plumbing");
        category.setDefaultSlaHours(48);

        Location location = new Location();
        location.setFloor("First");
        location.setRoomNumber("204");

        complaint = new Complaint();
        complaint.setTitle("Leaking tap");
        complaint.setDescription("Dripping water");
        complaint.setCategory(categoryRepository.save(category));
        complaint.setLocation(locationRepository.save(location));
        complaint.setStudent(student);
        complaint = complaintRepository.save(complaint);
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
    void getComplaintHistory_returnsOldestRecordFirst() {

        transactionTemplate.executeWithoutResult(tx -> {
            historyRepository.save(history(Status.PENDING, Status.IN_PROGRESS, null));
            historyRepository.save(history(Status.IN_PROGRESS, Status.RESOLVED, null));
            historyRepository.flush();
        });

        List<ComplaintHistory> result = complaintService.getComplaintHistory(
                complaint.getId(), student.getEmail());

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getChangedAt())
                .isBeforeOrEqualTo(result.get(1).getChangedAt());
    }

    private ComplaintHistory history(
            Status oldStatus, Status newStatus, LocalDateTime changedAt) {
        ComplaintHistory history = new ComplaintHistory();
        history.setComplaint(complaint);
        history.setActor(admin);
        history.setOldStatus(oldStatus);
        history.setNewStatus(newStatus);
        history.setChangedAt(changedAt);
        return history;
    }

    private User user(String email, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setName(email);
        user.setRole(role);
        return user;
    }
}
package com.example.demo.hostel.repository;

import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.model.Role;
import com.example.demo.hostel.model.Status;
import com.example.demo.hostel.model.User;
import com.example.demo.hostel.specification.ComplaintSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:complaint-filter-test",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class ComplaintFilterSpecificationTest {

    @Autowired
    private ComplaintRepository complaintRepository;

    @Autowired
    private UserRepository userRepository;

    private LocalDateTime from;
    private LocalDateTime to;

    @BeforeEach
    void setUp() {
        User student = new User();
        student.setEmail("student@smail.iitm.ac.in");
        student.setName("Student");
        student.setRole(Role.STUDENT);
        student = userRepository.save(student);

        LocalDateTime now = LocalDateTime.now();
        from = now.minusDays(1);
        to = now.plusDays(1);

        complaintRepository.save(complaint(
                student, " Second ", " Plumbing ", Status.PENDING,
                " ADMIN@SMAIL.IITM.AC.IN ", now
        ));
        complaintRepository.save(complaint(
                student, "First", "Electrical", Status.PENDING,
                "admin@smail.iitm.ac.in", now
        ));
        complaintRepository.save(complaint(
                student, "Second", "Plumbing", Status.RESOLVED,
                "other@smail.iitm.ac.in", now
        ));
    }

    @Test
    void filtersMatchFloorCategoryAndAssigneeIgnoringCaseAndWhitespace() {
        List<Complaint> results = complaintRepository.findAll(
                ComplaintSpecification.withFilters(
                        Status.PENDING,
                        " plumbing ",
                        " second ",
                        "ADMIN@SMAIL.IITM.AC.IN",
                        from,
                        to
                )
        );

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getFloor()).isEqualTo(" Second ");
    }

    private Complaint complaint(
            User student,
            String floor,
            String category,
            Status status,
            String assignedTo,
            LocalDateTime createdAt) {
        Complaint complaint = new Complaint();
        complaint.setTitle("Test complaint");
        complaint.setDescription("Filter test");
        complaint.setFloor(floor);
        complaint.setRoom("201");
        complaint.setCategory(category);
        complaint.setStatus(status);
        complaint.setAssignedTo(assignedTo);
        complaint.setStudent(student);
        complaint.setCreatedAt(createdAt);
        return complaint;
    }
}

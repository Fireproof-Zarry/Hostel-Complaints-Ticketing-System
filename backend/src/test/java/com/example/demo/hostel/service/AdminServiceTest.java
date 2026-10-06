package com.example.demo.hostel.service;

import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.model.Role;
import com.example.demo.hostel.model.Status;
import com.example.demo.hostel.model.User;
import com.example.demo.hostel.repository.ComplaintRepository;
import com.example.demo.hostel.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private ComplaintRepository complaintRepository;

    @Mock
    private ComplaintTransitionService transitionService;

    @Mock
    private UserRepository userRepository;

    private AdminService adminService;

    @BeforeEach
    void setUp() {
        adminService = new AdminService(
                complaintRepository,
                transitionService,
                userRepository
        );
    }

    @Test
    void whenNoFilters_thenReturnAllComplaints() {

        List<Complaint> complaints = List.of(
                new Complaint(),
                new Complaint()
        );

        when(complaintRepository.findAll()).thenReturn(complaints);

        List<Complaint> result =
                adminService.getComplaints(null, null, null, null);

        assertEquals(2, result.size());

        verify(complaintRepository).findAll();
    }

    @Test
    void whenStatusFilter_thenFindByStatus() {

        List<Complaint> complaints = List.of(new Complaint());

        when(complaintRepository.findByStatus(Status.PENDING))
                .thenReturn(complaints);

        List<Complaint> result =
                adminService.getComplaints(
                        Status.PENDING,
                        null,
                        null,
                        null
                );

        assertEquals(1, result.size());

        verify(complaintRepository)
                .findByStatus(Status.PENDING);
    }

    @Test
    void whenCategoryFilter_thenFindByCategory() {

        List<Complaint> complaints = List.of(new Complaint());

        when(complaintRepository.findByCategory("PLUMBING"))
                .thenReturn(complaints);

        List<Complaint> result =
                adminService.getComplaints(
                        null,
                        "PLUMBING",
                        null,
                        null
                );

        assertEquals(1, result.size());

        verify(complaintRepository)
                .findByCategory("PLUMBING");
    }

    @Test
    void whenDateFilter_thenFindByDateRange() {

        LocalDateTime from =
                LocalDateTime.of(2026, 10, 1, 0, 0);

        LocalDateTime to =
                LocalDateTime.of(2026, 10, 5, 23, 59);

        List<Complaint> complaints = List.of(new Complaint());

        when(complaintRepository.findByCreatedAtBetween(from, to))
                .thenReturn(complaints);

        List<Complaint> result =
                adminService.getComplaints(
                        null,
                        null,
                        from,
                        to
                );

        assertEquals(1, result.size());

        verify(complaintRepository)
                .findByCreatedAtBetween(from, to);
    }

    @Test
    void updateStatus_whenTransitionIsValid_thenSavesComplaint() {

        Complaint complaint = new Complaint();
        complaint.setStatus(Status.PENDING);

        when(complaintRepository.findById(1L))
                .thenReturn(Optional.of(complaint));

        when(complaintRepository.save(complaint))
                .thenReturn(complaint);

        Complaint result = adminService.updateStatus(
                1L,
                Status.ASSIGNED
        );

        verify(transitionService)
                .validateTransition(
                        Status.PENDING,
                        Status.ASSIGNED
                );

        verify(complaintRepository)
                .save(complaint);

        assertEquals(Status.ASSIGNED, result.getStatus());
    }

    @Test
    void updateStatus_whenTransitionIsInvalid_thenDoesNotSave() {

        Complaint complaint = new Complaint();
        complaint.setStatus(Status.PENDING);

        when(complaintRepository.findById(1L))
                .thenReturn(Optional.of(complaint));

        doThrow(new IllegalArgumentException("Invalid status transition"))
                .when(transitionService)
                .validateTransition(
                        Status.PENDING,
                        Status.RESOLVED
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> adminService.updateStatus(
                        1L,
                        Status.RESOLVED
                )
        );

        verify(complaintRepository, never())
                .save(complaint);
    }

    @Test
    void assignComplaint_whenUserIsAdmin_thenAssignsComplaint() {

        Complaint complaint = new Complaint();

        User admin = new User();
        admin.setEmail("admin@smail.iitm.ac.in");
        admin.setRole(Role.ADMIN);

        when(complaintRepository.findById(1L))
                .thenReturn(Optional.of(complaint));

        when(userRepository.findByEmail("admin@smail.iitm.ac.in"))
                .thenReturn(Optional.of(admin));

        when(complaintRepository.save(complaint))
                .thenReturn(complaint);

        Complaint result = adminService.assignComplaint(
                1L,
                "admin@smail.iitm.ac.in"
        );

        assertEquals(
                "admin@smail.iitm.ac.in",
                result.getAssignedTo()
        );

        verify(userRepository)
                .findByEmail("admin@smail.iitm.ac.in");

        verify(complaintRepository)
                .save(complaint);
    }

    @Test
    void assignComplaint_whenUserDoesNotExist_thenThrowsException() {

        Complaint complaint = new Complaint();

        when(complaintRepository.findById(1L))
                .thenReturn(Optional.of(complaint));

        when(userRepository.findByEmail("unknown@smail.iitm.ac.in"))
                .thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> adminService.assignComplaint(
                        1L,
                        "unknown@smail.iitm.ac.in"
                )
        );

        verify(complaintRepository, never())
                .save(complaint);
    }

    @Test
    void assignComplaint_whenUserIsStudent_thenThrowsException() {

        Complaint complaint = new Complaint();

        User student = new User();
        student.setEmail("student@smail.iitm.ac.in");
        student.setRole(Role.STUDENT);

        when(complaintRepository.findById(1L))
                .thenReturn(Optional.of(complaint));

        when(userRepository.findByEmail("student@smail.iitm.ac.in"))
                .thenReturn(Optional.of(student));

        assertThrows(
                IllegalArgumentException.class,
                () -> adminService.assignComplaint(
                        1L,
                        "student@smail.iitm.ac.in"
                )
        );

        verify(complaintRepository, never())
                .save(complaint);
    }
}
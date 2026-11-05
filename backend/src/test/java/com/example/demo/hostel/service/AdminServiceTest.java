package com.example.demo.hostel.service;

import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.model.Role;
import com.example.demo.hostel.model.Status;
import com.example.demo.hostel.model.User;
import com.example.demo.hostel.model.WorkOrderPriority;
import com.example.demo.hostel.repository.ComplaintRepository;
import com.example.demo.hostel.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.jpa.domain.Specification;

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

    @Mock
    private WorkOrderService workOrderService;

    private AdminService adminService;

    @BeforeEach
    void setUp() {
        adminService = new AdminService(
                complaintRepository,
                transitionService,
                userRepository,
                workOrderService
        );
    }

    @Test
    void whenNoFilters_thenReturnAllComplaints() {

        List<Complaint> complaints = List.of(
                new Complaint(),
                new Complaint()
        );

        when(complaintRepository.findAll(any(Specification.class)))
                .thenReturn(complaints);

        List<Complaint> result =
                adminService.getComplaints(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null, null
                );

        assertEquals(2, result.size());

        verify(complaintRepository).findAll(any(Specification.class));
    }

    @Test
    void whenStatusFilter_thenReturnMatchingComplaints() {

        List<Complaint> complaints = List.of(new Complaint());

        when(complaintRepository.findAll(any(Specification.class)))
                .thenReturn(complaints);

        List<Complaint> result =
                adminService.getComplaints(
                        Status.PENDING,
                        null,
                        null,
                        null,
                        null,
                        null, null
                );

        assertEquals(1, result.size());

        verify(complaintRepository).findAll(any(Specification.class));
    }

    @Test
    void whenCategoryFilter_thenReturnMatchingComplaints() {

        List<Complaint> complaints = List.of(new Complaint());

        when(complaintRepository.findAll(any(Specification.class)))
                .thenReturn(complaints);

        List<Complaint> result =
                adminService.getComplaints(
                        null,
                        "PLUMBING",
                        null,
                        null,
                        null,
                        null, null
                );

        assertEquals(1, result.size());

        verify(complaintRepository).findAll(any(Specification.class));
    }

    @Test
    void whenDateFilter_thenReturnMatchingComplaints() {

        LocalDateTime from =
                LocalDateTime.of(2026, 10, 1, 0, 0);

        LocalDateTime to =
                LocalDateTime.of(2026, 10, 5, 23, 59);

        List<Complaint> complaints = List.of(new Complaint());

        when(complaintRepository.findAll(any(Specification.class)))
                .thenReturn(complaints);

        List<Complaint> result =
                adminService.getComplaints(
                        null,
                        null,
                        null,
                        null,
                        from,
                        to, null
                );

        assertEquals(1, result.size());

        verify(complaintRepository).findAll(any(Specification.class));
    }

    @Test
    void updateStatus_delegatesToTransactionalTransitionWithActor() {

        User admin = new User();
        admin.setId("admin-1");
        admin.setEmail("admin@smail.iitm.ac.in");
        admin.setRole(Role.ADMIN);

        Complaint updated = new Complaint();
        updated.setStatus(Status.ASSIGNED);

        when(userRepository.findByEmail("admin@smail.iitm.ac.in"))
                .thenReturn(Optional.of(admin));

        when(complaintRepository.findById(1L))
                .thenReturn(Optional.of(updated));

        Complaint result = adminService.updateStatus(
                1L,
                Status.ASSIGNED,
                "admin@smail.iitm.ac.in",
                "Electrician scheduled"
        );

        verify(transitionService)
                .changeComplaintStatus(
                        1L,
                        "admin-1",
                        "ASSIGNED",
                        "Electrician scheduled"
                );

        assertEquals(Status.ASSIGNED, result.getStatus());
    }

    @Test
    void updateStatus_whenTransitionIsInvalid_thenPropagatesError() {

        User admin = new User();
        admin.setId("admin-1");

        when(userRepository.findByEmail("admin@smail.iitm.ac.in"))
                .thenReturn(Optional.of(admin));

        doThrow(new IllegalArgumentException("Invalid status transition"))
                .when(transitionService)
                .changeComplaintStatus(1L, "admin-1", "RESOLVED", null);

        assertThrows(
                IllegalArgumentException.class,
                () -> adminService.updateStatus(
                        1L,
                        Status.RESOLVED,
                        "admin@smail.iitm.ac.in",
                        null
                )
        );

        verify(complaintRepository, never())
                .findById(1L);
    }

    @Test
    void updateStatus_whenAdminUnknown_thenDoesNotChangeStatus() {

        when(userRepository.findByEmail("ghost@smail.iitm.ac.in"))
                .thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> adminService.updateStatus(
                        1L,
                        Status.RESOLVED,
                        "ghost@smail.iitm.ac.in",
                        null
                )
        );

        verifyNoInteractions(transitionService);
    }

    @Test
    void assignComplaint_createsAWorkOrderWithDefaultPriority() {

        Complaint complaint = new Complaint();
        complaint.setAssignedTo("warden@gmail.com");

        User staff = new User();
        staff.setId("staff-1");
        staff.setEmail("warden@gmail.com");
        staff.setRole(Role.STAFF);

        when(userRepository.findByEmail("warden@gmail.com"))
                .thenReturn(Optional.of(staff));
        when(complaintRepository.findById(1L))
                .thenReturn(Optional.of(complaint));

        Complaint result = adminService.assignComplaint(
                1L, "warden@gmail.com", null, null, "admin@smail.iitm.ac.in");

        verify(workOrderService).assignInCharge(
                1L, "staff-1", WorkOrderPriority.MEDIUM, null, "admin@smail.iitm.ac.in");
        assertEquals("warden@gmail.com", result.getAssignedTo());
    }

    @Test
    void assignComplaint_passesPriorityAndDeadlineThrough() {

        User staff = new User();
        staff.setId("staff-1");
        staff.setRole(Role.STAFF);
        LocalDateTime deadline = LocalDateTime.now().plusDays(2);

        when(userRepository.findByEmail("warden@gmail.com"))
                .thenReturn(Optional.of(staff));
        when(complaintRepository.findById(1L))
                .thenReturn(Optional.of(new Complaint()));

        adminService.assignComplaint(1L, "warden@gmail.com", WorkOrderPriority.URGENT, deadline, "admin@smail.iitm.ac.in");

        verify(workOrderService).assignInCharge(
                1L, "staff-1", WorkOrderPriority.URGENT, deadline, "admin@smail.iitm.ac.in");
    }

    @Test
    void assignComplaint_whenUserDoesNotExist_thenThrowsException() {

        when(userRepository.findByEmail("unknown@smail.iitm.ac.in"))
                .thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> adminService.assignComplaint(
                        1L, "unknown@smail.iitm.ac.in", null, null, "admin@smail.iitm.ac.in")
        );

        verifyNoInteractions(workOrderService);
    }
}

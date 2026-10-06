package com.example.demo.hostel.service;

import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.model.Status;
import com.example.demo.hostel.repository.ComplaintRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private ComplaintRepository complaintRepository;

    @Mock
    private ComplaintTransitionService transitionService;

    private AdminService adminService;

    @BeforeEach
    void setUp() {
        adminService = new AdminService(
                complaintRepository,
                transitionService
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
                .thenReturn(java.util.Optional.of(complaint));

        when(complaintRepository.save(complaint))
                .thenReturn(complaint);

        AdminService service = adminService;

        Complaint result = service.updateStatus(
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
            .thenReturn(java.util.Optional.of(complaint));

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
}
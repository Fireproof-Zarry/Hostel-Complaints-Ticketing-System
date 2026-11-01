
package com.example.demo.hostel.service;

import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.model.ComplaintHistory;
import com.example.demo.hostel.model.Role;
import com.example.demo.hostel.model.Status;
import com.example.demo.hostel.model.User;
import com.example.demo.hostel.model.WorkOrder;
import com.example.demo.hostel.repository.ComplaintHistoryRepository;
import com.example.demo.hostel.repository.ComplaintRepository;
import com.example.demo.hostel.repository.UserRepository;
import com.example.demo.hostel.repository.WorkOrderRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkOrderServiceTest {

    @Mock
    private ComplaintRepository complaintRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private WorkOrderRepository workOrderRepository;

    @Mock
    private ComplaintHistoryRepository complaintHistoryRepository;

    @Mock
    private ComplaintTransitionService complaintTransitionService;

    @InjectMocks
    private WorkOrderService workOrderService;

    private Complaint complaint;
    private User assignee;
    private WorkOrder workOrder;

    private final Long complaintId = 1L;
    private final String assigneeId = "admin-1";
    private final UUID workOrderId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        complaint = new Complaint();
        complaint.setId(complaintId);
        complaint.setStatus(Status.PENDING);

        assignee = new User();
        assignee.setId(assigneeId);
        assignee.setEmail("admin@example.com");
        assignee.setRole(Role.ADMIN);

        workOrder = new WorkOrder();
        workOrder.setComplaint(complaint);
        workOrder.setAssignee(assignee);
        workOrder.setPriority("HIGH");
        workOrder.setStatus(WorkOrder.STATUS_ASSIGNED);
    }

    // --------------------------------------------------
    // ASSIGN WORK ORDER
    // --------------------------------------------------

    @Test
    void assignWorkOrder_success() {
        LocalDateTime deadline = LocalDateTime.now().plusDays(2);

        when(complaintRepository.findById(complaintId))
                .thenReturn(Optional.of(complaint));
        when(workOrderRepository.existsByComplaint(complaint))
                .thenReturn(false);
        when(userRepository.findById(assigneeId))
                .thenReturn(Optional.of(assignee));
        when(workOrderRepository.save(any(WorkOrder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WorkOrder result = workOrderService.assignWorkOrder(
                complaintId, assigneeId, " high ", deadline);

        assertNotNull(result);
        assertEquals("HIGH", result.getPriority());
        assertEquals(WorkOrder.STATUS_ASSIGNED, result.getStatus());
        assertEquals(deadline, result.getDeadline());
        assertEquals(Status.ASSIGNED, complaint.getStatus());
        assertEquals("admin@example.com", complaint.getAssignedTo());

        verify(complaintRepository).save(complaint);
        verify(complaintHistoryRepository).save(any(ComplaintHistory.class));
        verify(workOrderRepository).save(any(WorkOrder.class));
        verify(complaintTransitionService)
                .validateTransition(Status.PENDING, Status.ASSIGNED);
    }

    @Test
    void assignWorkOrder_rejectsMissingPriority() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> workOrderService.assignWorkOrder(
                        complaintId, assigneeId, null, null));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(complaintRepository, userRepository);
    }

    @Test
    void assignWorkOrder_rejectsBlankPriority() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> workOrderService.assignWorkOrder(
                        complaintId, assigneeId, " ", null));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(complaintRepository, userRepository);
    }

    @Test
    void assignWorkOrder_rejectsInvalidPriority() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> workOrderService.assignWorkOrder(
                        complaintId, assigneeId, "CRITICAL", null));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(complaintRepository, userRepository);
    }

    @Test
    void assignWorkOrder_complaintNotFound() {
        when(complaintRepository.findById(complaintId))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> workOrderService.assignWorkOrder(
                        complaintId, assigneeId, "HIGH", null));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(workOrderRepository, never()).save(any());
    }

    @Test
    void assignWorkOrder_rejectsDuplicateWorkOrder() {
        when(complaintRepository.findById(complaintId))
                .thenReturn(Optional.of(complaint));
        when(workOrderRepository.existsByComplaint(complaint))
                .thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> workOrderService.assignWorkOrder(
                        complaintId, assigneeId, "HIGH", null));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(userRepository, never()).findById(anyString());
        verify(workOrderRepository, never()).save(any());
    }

    @Test
    void assignWorkOrder_assigneeNotFound() {
        when(complaintRepository.findById(complaintId))
                .thenReturn(Optional.of(complaint));
        when(workOrderRepository.existsByComplaint(complaint))
                .thenReturn(false);
        when(userRepository.findById(assigneeId))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> workOrderService.assignWorkOrder(
                        complaintId, assigneeId, "HIGH", null));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(workOrderRepository, never()).save(any());
    }

    @Test
    void assignWorkOrder_rejectsNonAdminAssignee() {
        assignee.setRole(Role.STUDENT);

        when(complaintRepository.findById(complaintId))
                .thenReturn(Optional.of(complaint));
        when(workOrderRepository.existsByComplaint(complaint))
                .thenReturn(false);
        when(userRepository.findById(assigneeId))
                .thenReturn(Optional.of(assignee));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> workOrderService.assignWorkOrder(
                        complaintId, assigneeId, "HIGH", null));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(workOrderRepository, never()).save(any());
    }

    // --------------------------------------------------
    // GET WORK ORDERS
    // --------------------------------------------------

    @Test
    void getAllWorkOrders_returnsAll() {
        when(workOrderRepository.findAll())
                .thenReturn(List.of(workOrder));

        List<WorkOrder> result = workOrderService.getAllWorkOrders();

        assertEquals(1, result.size());
        assertSame(workOrder, result.get(0));
    }

    @Test
    void getWorkOrder_returnsWorkOrder() {
        when(workOrderRepository.findById(workOrderId))
                .thenReturn(Optional.of(workOrder));

        WorkOrder result = workOrderService.getWorkOrder(workOrderId);

        assertSame(workOrder, result);
    }

    @Test
    void getWorkOrder_notFound() {
        when(workOrderRepository.findById(workOrderId))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> workOrderService.getWorkOrder(workOrderId));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void getWorkOrdersByAssignee_returnsMatchingOrders() {
        when(workOrderRepository.findByAssignee_Id(assigneeId))
                .thenReturn(List.of(workOrder));

        List<WorkOrder> result = workOrderService.getWorkOrdersByAssignee(assigneeId);

        assertEquals(1, result.size());
        verify(workOrderRepository).findByAssignee_Id(assigneeId);
    }

    // --------------------------------------------------
    // UPDATE WORK ORDER STATUS
    // --------------------------------------------------

    @Test
    void updateWorkOrderStatus_assignedToInProgress() {
        workOrder.setStatus("ASSIGNED");

        when(workOrderRepository.findById(workOrderId))
                .thenReturn(Optional.of(workOrder));
        when(workOrderRepository.save(any(WorkOrder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WorkOrder result = workOrderService.updateWorkOrderStatus(
                workOrderId, "IN_PROGRESS", null);

        assertEquals("IN_PROGRESS", result.getStatus());

        verify(workOrderRepository).save(workOrder);
        verify(complaintTransitionService).changeComplaintStatus(
                complaintId,
                assigneeId,
                "IN_PROGRESS",
                "Work order status changed to IN_PROGRESS");
    }

    @Test
    void updateWorkOrderStatus_inProgressToCompleted() {
        workOrder.setStatus("IN_PROGRESS");

        when(workOrderRepository.findById(workOrderId))
                .thenReturn(Optional.of(workOrder));
        when(workOrderRepository.save(any(WorkOrder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WorkOrder result = workOrderService.updateWorkOrderStatus(
                workOrderId, "COMPLETED", "Fan replaced");

        assertEquals("COMPLETED", result.getStatus());
        assertEquals("Fan replaced", result.getCompletionNotes());

        verify(complaintTransitionService).changeComplaintStatus(
                complaintId,
                assigneeId,
                "RESOLVED",
                "Work order status changed to COMPLETED");
    }

    @Test
    void updateWorkOrderStatus_normalizesStatus() {
        workOrder.setStatus("ASSIGNED");

        when(workOrderRepository.findById(workOrderId))
                .thenReturn(Optional.of(workOrder));
        when(workOrderRepository.save(any(WorkOrder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WorkOrder result = workOrderService.updateWorkOrderStatus(
                workOrderId, " in_progress ", null);

        assertEquals("IN_PROGRESS", result.getStatus());
    }

    @Test
    void updateWorkOrderStatus_rejectsMissingStatus() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> workOrderService.updateWorkOrderStatus(
                        workOrderId, null, null));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(workOrderRepository, never()).findById(any());
    }

    @Test
    void updateWorkOrderStatus_rejectsBlankStatus() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> workOrderService.updateWorkOrderStatus(
                        workOrderId, " ", null));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void updateWorkOrderStatus_workOrderNotFound() {
        when(workOrderRepository.findById(workOrderId))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> workOrderService.updateWorkOrderStatus(
                        workOrderId, "IN_PROGRESS", null));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void updateWorkOrderStatus_rejectsInvalidTransition() {
        workOrder.setStatus("ASSIGNED");

        when(workOrderRepository.findById(workOrderId))
                .thenReturn(Optional.of(workOrder));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> workOrderService.updateWorkOrderStatus(
                        workOrderId, "COMPLETED", null));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(workOrderRepository, never()).save(any());
        verify(complaintTransitionService, never())
                .changeComplaintStatus(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void updateWorkOrderStatus_rejectsTransitionFromCompleted() {
        workOrder.setStatus("COMPLETED");

        when(workOrderRepository.findById(workOrderId))
                .thenReturn(Optional.of(workOrder));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> workOrderService.updateWorkOrderStatus(
                        workOrderId, "IN_PROGRESS", null));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(workOrderRepository, never()).save(any());
    }

    @Test
    void updateWorkOrderStatus_trimsCompletionNotes() {
        workOrder.setStatus("IN_PROGRESS");

        when(workOrderRepository.findById(workOrderId))
                .thenReturn(Optional.of(workOrder));
        when(workOrderRepository.save(any(WorkOrder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WorkOrder result = workOrderService.updateWorkOrderStatus(
                workOrderId, "COMPLETED", "  Repaired tap  ");

        assertEquals("Repaired tap", result.getCompletionNotes());
    }

    @Test
    void updateWorkOrderStatus_blankCompletionNotesBecomeNull() {
        workOrder.setStatus("IN_PROGRESS");

        when(workOrderRepository.findById(workOrderId))
                .thenReturn(Optional.of(workOrder));
        when(workOrderRepository.save(any(WorkOrder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WorkOrder result = workOrderService.updateWorkOrderStatus(
                workOrderId, "COMPLETED", "   ");

        assertNull(result.getCompletionNotes());
    }
}

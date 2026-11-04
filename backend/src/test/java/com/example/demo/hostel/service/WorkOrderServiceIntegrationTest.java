package com.example.demo.hostel.service;

import com.example.demo.hostel.dto.UpdateWorkOrderRequest;
import com.example.demo.hostel.dto.WorkOrderView;
import com.example.demo.hostel.model.Category;
import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.model.ComplaintHistory;
import com.example.demo.hostel.model.Location;
import com.example.demo.hostel.model.Role;
import com.example.demo.hostel.model.Status;
import com.example.demo.hostel.model.User;
import com.example.demo.hostel.model.WorkOrderPriority;
import com.example.demo.hostel.model.WorkOrderStatus;
import com.example.demo.hostel.repository.CategoryRepository;
import com.example.demo.hostel.repository.ComplaintHistoryRepository;
import com.example.demo.hostel.repository.ComplaintRepository;
import com.example.demo.hostel.repository.LocationRepository;
import com.example.demo.hostel.repository.UserRepository;
import com.example.demo.hostel.repository.WorkOrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:work-order-test",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class WorkOrderServiceIntegrationTest {

    private static final String WARDEN = "warden@gmail.com";
    private static final String CARETAKER = "caretaker@gmail.com";
    private static final String ADMIN = "admin@smail.iitm.ac.in";
    private static final String STUDENT = "student@smail.iitm.ac.in";

    @Autowired private WorkOrderService workOrderService;
    @Autowired private AdminService adminService;
    @Autowired private WorkOrderRepository workOrderRepository;
    @Autowired private ComplaintRepository complaintRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private ComplaintHistoryRepository historyRepository;

    private User warden;
    private User caretaker;
    private User admin;
    private User student;
    private Category category;
    private Location location;
    private Long complaintId;

    @BeforeEach
    void setUp() {
        warden = userRepository.save(user(WARDEN, Role.STAFF));
        caretaker = userRepository.save(user(CARETAKER, Role.STAFF));
        admin = userRepository.save(user(ADMIN, Role.ADMIN));
        student = userRepository.save(user(STUDENT, Role.STUDENT));

        category = new Category();
        category.setName("Electrical");
        category.setDefaultSlaHours(48);
        category = categoryRepository.save(category);

        location = new Location();
        location.setFloor("First");
        location.setRoomNumber("204");
        location = locationRepository.save(location);

        complaintId = complaint("Fan not working");
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM work_orders");
        jdbcTemplate.update("DELETE FROM complaint_history");
        jdbcTemplate.update("DELETE FROM complaints");
        categoryRepository.deleteAll();
        locationRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ---- assigning ----

    @Test
    void staffSelfAssignMovesComplaintToAssignedAndLogsIt() {
        LocalDateTime deadline = LocalDateTime.now().plusDays(2);

        WorkOrderView view = workOrderService.assignInCharge(complaintId, null, WorkOrderPriority.HIGH, deadline, WARDEN);

        assertThat(view.assigneeId()).isEqualTo(warden.getId());
        assertThat(view.priority()).isEqualTo(WorkOrderPriority.HIGH);
        assertThat(view.status()).isEqualTo(WorkOrderStatus.OPEN);
        assertThat(view.deadline()).isEqualTo(deadline);
        Complaint complaint = complaint(complaintId);
        assertThat(complaint.getStatus()).isEqualTo(Status.ASSIGNED);
        assertThat(complaint.getAssignedTo()).isEqualTo(WARDEN);
        assertThat(history()).extracting(ComplaintHistory::getNewStatus).containsExactly(Status.ASSIGNED);
    }

    @Test
    void staffCannotAssignSomeoneElse() {
        assertStatus(() -> workOrderService.assignInCharge(complaintId, caretaker.getId(), WorkOrderPriority.LOW, null, WARDEN),
                HttpStatus.FORBIDDEN);
        assertThat(complaint(complaintId).getStatus()).isEqualTo(Status.PENDING);
    }

    @Test
    void staffCannotClaimAComplaintSomeoneElseHolds() {
        workOrderService.assignInCharge(complaintId, null, WorkOrderPriority.LOW, null, WARDEN);

        assertStatus(() -> workOrderService.assignInCharge(complaintId, null, WorkOrderPriority.LOW, null, CARETAKER),
                HttpStatus.CONFLICT);
        assertThat(complaint(complaintId).getAssignedTo()).isEqualTo(WARDEN);
    }

    @Test
    void adminCanAssignAndReassignWhileKeepingOneActiveWorkOrder() {
        WorkOrderView first = workOrderService.assignInCharge(complaintId, warden.getId(), WorkOrderPriority.LOW, null, ADMIN);
        WorkOrderView second = workOrderService.assignInCharge(complaintId, caretaker.getId(), WorkOrderPriority.URGENT, null, ADMIN);

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.assigneeId()).isEqualTo(caretaker.getId());
        assertThat(second.priority()).isEqualTo(WorkOrderPriority.URGENT);
        assertThat(workOrderRepository.count()).isEqualTo(1);
        assertThat(complaint(complaintId).getAssignedTo()).isEqualTo(CARETAKER);
        // Reassigning an ASSIGNED complaint is not another status change
        assertThat(history()).extracting(ComplaintHistory::getNewStatus).containsExactly(Status.ASSIGNED);
    }

    @Test
    void adminsCanBeAssignees() {
        WorkOrderView view = workOrderService.assignInCharge(complaintId, null, WorkOrderPriority.MEDIUM, null, ADMIN);

        assertThat(view.assigneeId()).isEqualTo(admin.getId());
    }

    @Test
    void studentsCanNeitherAssignNorBeAssigned() {
        assertStatus(() -> workOrderService.assignInCharge(complaintId, null, WorkOrderPriority.LOW, null, STUDENT),
                HttpStatus.FORBIDDEN);
        assertStatus(() -> workOrderService.assignInCharge(complaintId, student.getId(), WorkOrderPriority.LOW, null, ADMIN),
                HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsPastDeadlinesMissingPriorityAndClosedComplaints() {
        assertStatus(() -> workOrderService.assignInCharge(complaintId, null, WorkOrderPriority.LOW,
                LocalDateTime.now().minusHours(1), WARDEN), HttpStatus.BAD_REQUEST);
        assertStatus(() -> workOrderService.assignInCharge(complaintId, null, null, null, WARDEN), HttpStatus.BAD_REQUEST);

        Complaint closed = complaint(complaintId);
        closed.setStatus(Status.RESOLVED);
        complaintRepository.save(closed);
        assertStatus(() -> workOrderService.assignInCharge(complaintId, null, WorkOrderPriority.LOW, null, WARDEN),
                HttpStatus.CONFLICT);
    }

    @Test
    void assignmentRollsBackIfLoggingTheStatusChangeFails() {
        doThrow(new RuntimeException("history write failed")).when(historyRepository).save(any(ComplaintHistory.class));

        assertThatThrownBy(() -> workOrderService.assignInCharge(complaintId, null, WorkOrderPriority.LOW, null, WARDEN))
                .hasMessageContaining("history write failed");

        assertThat(workOrderRepository.count()).isZero();
        Complaint complaint = complaint(complaintId);
        assertThat(complaint.getStatus()).isEqualTo(Status.PENDING);
        assertThat(complaint.getAssignedTo()).isNull();
    }

    // ---- updating ----

    @Test
    void inProgressThenDoneResolvesTheComplaintWithTheNotes() {
        WorkOrderView view = workOrderService.assignInCharge(complaintId, null, WorkOrderPriority.MEDIUM, null, WARDEN);

        workOrderService.updateWorkOrder(view.id(), new UpdateWorkOrderRequest(null, null, null, WorkOrderStatus.IN_PROGRESS), WARDEN);
        assertThat(complaint(complaintId).getStatus()).isEqualTo(Status.IN_PROGRESS);

        WorkOrderView done = workOrderService.updateWorkOrder(view.id(),
                new UpdateWorkOrderRequest(null, null, "Electrician replaced the capacitor", WorkOrderStatus.DONE), WARDEN);

        assertThat(done.status()).isEqualTo(WorkOrderStatus.DONE);
        assertThat(done.completionNotes()).isEqualTo("Electrician replaced the capacitor");
        assertThat(complaint(complaintId).getStatus()).isEqualTo(Status.RESOLVED);
        List<ComplaintHistory> history = history();
        assertThat(history).extracting(ComplaintHistory::getNewStatus)
                .containsExactly(Status.ASSIGNED, Status.IN_PROGRESS, Status.RESOLVED);
        assertThat(history.getLast().getReason()).isEqualTo("Electrician replaced the capacitor");
    }

    @Test
    void cancellingReturnsTheComplaintToPendingAndUnassigned() {
        WorkOrderView view = workOrderService.assignInCharge(complaintId, null, WorkOrderPriority.MEDIUM, null, WARDEN);

        workOrderService.updateWorkOrder(view.id(), new UpdateWorkOrderRequest(null, null, null, WorkOrderStatus.CANCELLED), WARDEN);

        Complaint complaint = complaint(complaintId);
        assertThat(complaint.getStatus()).isEqualTo(Status.PENDING);
        assertThat(complaint.getAssignedTo()).isNull();
        // Claimable again
        WorkOrderView again = workOrderService.assignInCharge(complaintId, null, WorkOrderPriority.LOW, null, CARETAKER);
        assertThat(again.assigneeId()).isEqualTo(caretaker.getId());
    }

    @Test
    void updatesPriorityDeadlineAndNotesWithoutChangingStatus() {
        WorkOrderView view = workOrderService.assignInCharge(complaintId, null, WorkOrderPriority.LOW, null, WARDEN);
        LocalDateTime deadline = LocalDateTime.now().plusDays(3);

        WorkOrderView updated = workOrderService.updateWorkOrder(view.id(),
                new UpdateWorkOrderRequest(WorkOrderPriority.URGENT, deadline, "Worker booked for Thursday", null), WARDEN);

        assertThat(updated.priority()).isEqualTo(WorkOrderPriority.URGENT);
        assertThat(updated.deadline()).isEqualTo(deadline);
        assertThat(updated.completionNotes()).isEqualTo("Worker booked for Thursday");
        assertThat(updated.status()).isEqualTo(WorkOrderStatus.OPEN);
    }

    @Test
    void onlyTheAssigneeOrAnAdminCanUpdate() {
        WorkOrderView view = workOrderService.assignInCharge(complaintId, null, WorkOrderPriority.LOW, null, WARDEN);
        UpdateWorkOrderRequest request = new UpdateWorkOrderRequest(WorkOrderPriority.HIGH, null, null, null);

        assertStatus(() -> workOrderService.updateWorkOrder(view.id(), request, CARETAKER), HttpStatus.FORBIDDEN);
        assertThat(workOrderService.updateWorkOrder(view.id(), request, ADMIN).priority()).isEqualTo(WorkOrderPriority.HIGH);
    }

    @Test
    void rejectsInvalidTransitionsAndChangesToFinishedWorkOrders() {
        WorkOrderView view = workOrderService.assignInCharge(complaintId, null, WorkOrderPriority.LOW, null, WARDEN);
        workOrderService.updateWorkOrder(view.id(), new UpdateWorkOrderRequest(null, null, null, WorkOrderStatus.IN_PROGRESS), WARDEN);

        assertStatus(() -> workOrderService.updateWorkOrder(view.id(),
                new UpdateWorkOrderRequest(null, null, null, WorkOrderStatus.OPEN), WARDEN), HttpStatus.BAD_REQUEST);

        workOrderService.updateWorkOrder(view.id(), new UpdateWorkOrderRequest(null, null, null, WorkOrderStatus.DONE), WARDEN);
        assertStatus(() -> workOrderService.updateWorkOrder(view.id(),
                new UpdateWorkOrderRequest(WorkOrderPriority.HIGH, null, null, null), WARDEN), HttpStatus.CONFLICT);
    }

    // ---- queue ----

    @Test
    void queueIsActiveWorkOrdersByDeadlineThenUrgency() {
        LocalDateTime soon = LocalDateTime.now().plusDays(1);
        Long laterLow = complaint("Later, low");
        Long soonLow = complaint("Soon, low");
        Long noDeadlineUrgent = complaint("No deadline, urgent");
        Long noDeadlineLow = complaint("No deadline, low");
        Long finished = complaint("Finished");

        workOrderService.assignInCharge(laterLow, null, WorkOrderPriority.LOW, soon.plusDays(5), WARDEN);
        workOrderService.assignInCharge(soonLow, null, WorkOrderPriority.LOW, soon, WARDEN);
        workOrderService.assignInCharge(noDeadlineLow, null, WorkOrderPriority.LOW, null, WARDEN);
        workOrderService.assignInCharge(noDeadlineUrgent, null, WorkOrderPriority.URGENT, null, WARDEN);
        WorkOrderView done = workOrderService.assignInCharge(finished, null, WorkOrderPriority.URGENT, soon, WARDEN);
        workOrderService.updateWorkOrder(done.id(), new UpdateWorkOrderRequest(null, null, null, WorkOrderStatus.DONE), WARDEN);
        workOrderService.assignInCharge(complaintId, null, WorkOrderPriority.URGENT, soon, CARETAKER);

        assertThat(workOrderService.getMyQueue(WARDEN)).extracting(WorkOrderView::complaintId)
                .containsExactly(soonLow, laterLow, noDeadlineUrgent, noDeadlineLow);
    }

    @Test
    void complaintWorkOrdersAreListedNewestFirst() {
        WorkOrderView cancelled = workOrderService.assignInCharge(complaintId, null, WorkOrderPriority.LOW, null, WARDEN);
        workOrderService.updateWorkOrder(cancelled.id(), new UpdateWorkOrderRequest(null, null, null, WorkOrderStatus.CANCELLED), WARDEN);
        WorkOrderView current = workOrderService.assignInCharge(complaintId, null, WorkOrderPriority.HIGH, null, CARETAKER);

        assertThat(workOrderService.getForComplaint(complaintId, ADMIN)).extracting(WorkOrderView::id)
                .containsExactly(current.id(), cancelled.id());
    }

    // ---- complaint status changed directly by an admin ----

    @Test
    void resolvingTheComplaintDirectlyFinishesItsWorkOrder() {
        WorkOrderView workOrder = assignToWarden();

        adminService.updateStatus(complaintId, Status.RESOLVED, ADMIN, "Fixed by the contractor");

        assertThat(workOrderStatus(workOrder)).isEqualTo(WorkOrderStatus.DONE);
        assertThat(complaint(complaintId).getAssignedTo()).isEqualTo(WARDEN);
    }

    @Test
    void rejectingTheComplaintDirectlyCancelsItsWorkOrderAndItCannotReviveIt() {
        WorkOrderView workOrder = assignToWarden();

        adminService.updateStatus(complaintId, Status.REJECTED, ADMIN, "Not a hostel issue");

        assertThat(workOrderStatus(workOrder)).isEqualTo(WorkOrderStatus.CANCELLED);
        assertStatus(() -> workOrderService.updateWorkOrder(workOrder.id(),
                new UpdateWorkOrderRequest(null, null, null, WorkOrderStatus.IN_PROGRESS), WARDEN), HttpStatus.CONFLICT);
        assertThat(complaint(complaintId).getStatus()).isEqualTo(Status.REJECTED);
    }

    @Test
    void movingTheComplaintBackToPendingCancelsAndUnassigns() {
        WorkOrderView workOrder = assignToWarden();

        adminService.updateStatus(complaintId, Status.PENDING, ADMIN, null);

        assertThat(workOrderStatus(workOrder)).isEqualTo(WorkOrderStatus.CANCELLED);
        assertThat(complaint(complaintId).getAssignedTo()).isNull();
        assertThat(workOrderService.getMyQueue(WARDEN)).isEmpty();
    }

    @Test
    void movingTheComplaintBetweenAssignedAndInProgressMovesTheWorkOrderToo() {
        WorkOrderView workOrder = assignToWarden();

        adminService.updateStatus(complaintId, Status.IN_PROGRESS, ADMIN, null);
        assertThat(workOrderStatus(workOrder)).isEqualTo(WorkOrderStatus.IN_PROGRESS);

        adminService.updateStatus(complaintId, Status.ASSIGNED, ADMIN, null);
        assertThat(workOrderStatus(workOrder)).isEqualTo(WorkOrderStatus.OPEN);
    }

    @Test
    void directStatusChangesWorkWithoutAWorkOrder() {
        adminService.updateStatus(complaintId, Status.RESOLVED, ADMIN, null);

        assertThat(complaint(complaintId).getStatus()).isEqualTo(Status.RESOLVED);
        assertThat(workOrderRepository.count()).isZero();
    }

    // ---- soft-deleted complaints ----

    @Test
    void softDeletedComplaintsDropOutOfQueuesAndBulkChanges() {
        assignToWarden();
        Long kept = complaint("Tap leaking");
        workOrderService.assignInCharge(kept, null, WorkOrderPriority.LOW, null, WARDEN);
        jdbcTemplate.update("UPDATE complaints SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", complaintId);

        assertThat(workOrderService.getMyQueue(WARDEN)).extracting(WorkOrderView::complaintId).containsExactly(kept);
        assertThat(workOrderService.cancelActiveWorkOrders(warden, admin)).isEqualTo(1);
        assertThat(complaint(kept).getStatus()).isEqualTo(Status.PENDING);
    }

    // ---- not found ----

    @Test
    void missingRecordsAreReported() {
        assertStatus(() -> workOrderService.assignInCharge(999_999L, null, WorkOrderPriority.LOW, null, WARDEN),
                HttpStatus.NOT_FOUND);
        assertStatus(() -> workOrderService.assignInCharge(complaintId, "no-such-user", WorkOrderPriority.LOW, null, ADMIN),
                HttpStatus.BAD_REQUEST);
        assertStatus(() -> workOrderService.updateWorkOrder(java.util.UUID.randomUUID(),
                new UpdateWorkOrderRequest(null, null, null, WorkOrderStatus.DONE), ADMIN), HttpStatus.NOT_FOUND);
        assertStatus(() -> workOrderService.getForComplaint(999_999L, ADMIN), HttpStatus.NOT_FOUND);
        assertStatus(() -> workOrderService.getMyQueue("nobody@gmail.com"), HttpStatus.NOT_FOUND);
    }

    // ---- completion notes ----

    @Test
    void completionNotesAreTrimmedAndBlankNotesAreCleared() {
        WorkOrderView workOrder = assignToWarden();

        WorkOrderView trimmed = workOrderService.updateWorkOrder(workOrder.id(),
                new UpdateWorkOrderRequest(null, null, "  Replaced the capacitor  ", null), WARDEN);
        assertThat(trimmed.completionNotes()).isEqualTo("Replaced the capacitor");

        WorkOrderView cleared = workOrderService.updateWorkOrder(workOrder.id(),
                new UpdateWorkOrderRequest(null, null, "   ", null), WARDEN);
        assertThat(cleared.completionNotes()).isNull();
    }

    private WorkOrderView assignToWarden() {
        return workOrderService.assignInCharge(complaintId, null, WorkOrderPriority.HIGH, null, WARDEN);
    }

    private WorkOrderStatus workOrderStatus(WorkOrderView workOrder) {
        return workOrderRepository.findById(workOrder.id()).orElseThrow().getStatus();
    }

    private Long complaint(String title) {
        Complaint complaint = new Complaint();
        complaint.setTitle(title);
        complaint.setDescription("Work order test");
        complaint.setCategory(category);
        complaint.setLocation(location);
        complaint.setStudent(student);
        return complaintRepository.save(complaint).getId();
    }

    private Complaint complaint(Long id) {
        return complaintRepository.findById(id).orElseThrow();
    }

    private List<ComplaintHistory> history() {
        return transactionTemplate.execute(tx -> {
            List<ComplaintHistory> entries = historyRepository.findByComplaintOrderByChangedAtAsc(complaint(complaintId));
            entries.forEach(entry -> entry.getNewStatus());
            return entries;
        });
    }

    private static void assertStatus(Runnable call, HttpStatus expected) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(expected));
    }

    private static User user(String email, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setName(email);
        user.setRole(role);
        return user;
    }
}

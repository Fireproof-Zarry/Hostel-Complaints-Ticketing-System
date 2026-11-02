package com.example.demo.hostel.service;

import com.example.demo.hostel.dto.StaffMemberView;
import com.example.demo.hostel.dto.WorkOrderView;
import com.example.demo.hostel.model.WorkOrder;
import com.example.demo.hostel.model.Role;
import com.example.demo.hostel.model.User;
import com.example.demo.hostel.repository.UserRepository;
import com.example.demo.hostel.repository.WorkOrderRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Admin-managed hostel staff. Staff are not students: any verified email can
 * be added, and AuthService keeps their STAFF role on every login.
 */
@Service
public class StaffService {

    private static final String EMAIL_PATTERN = "[^\\s@]+@[^\\s@]+\\.[^\\s@]+";

    private final UserRepository userRepository;
    private final WorkOrderRepository workOrderRepository;
    private final WorkOrderService workOrderService;
    private final Set<String> adminEmails;

    public StaffService(
            UserRepository userRepository,
            WorkOrderRepository workOrderRepository,
            WorkOrderService workOrderService,
            @Value("${app.auth.admin-emails:}") String configuredAdminEmails) {
        this.userRepository = userRepository;
        this.workOrderRepository = workOrderRepository;
        this.workOrderService = workOrderService;
        this.adminEmails = Arrays.stream(configuredAdminEmails.split(","))
                .map(String::trim)
                .filter(email -> !email.isEmpty())
                .map(email -> email.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Staff and admins (admins can be assignees too), with their current workload. */
    @Transactional(readOnly = true)
    public List<StaffMemberView> listStaff() {
        return userRepository.findByRoleInOrderByEmailAsc(List.of(Role.STAFF, Role.ADMIN)).stream()
                .map(this::toView)
                .toList();
    }

    /** One staff member's or admin's active work orders, in queue order. */
    @Transactional(readOnly = true)
    public List<WorkOrderView> getActiveWorkOrders(String userId) {
        return workOrderService.getActiveFor(requireAssignable(userId, "Staff member not found"));
    }

    /**
     * Makes this email a staff member. Works before their first sign-in: the
     * account is created now and their name is filled in when they log in.
     */
    @Transactional
    public StaffMemberView addStaff(String rawEmail) {
        String email = rawEmail == null ? "" : rawEmail.trim().toLowerCase(Locale.ROOT);
        if (!email.matches(EMAIL_PATTERN)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid email address");
        }
        if (adminEmails.contains(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This email is already an administrator");
        }

        User user = userRepository.findByEmail(email).orElseGet(() -> {
            User created = new User();
            created.setEmail(email);
            created.setName(email);
            return created;
        });
        user.setRole(Role.STAFF);
        return toView(userRepository.save(user));
    }

    /**
     * Removes a staff member. Their active work orders move to reassignToId
     * when given; otherwise they are cancelled (those complaints go back to
     * PENDING, logged in history). Either way they lose access.
     */
    @Transactional
    public void removeStaff(String userId, String reassignToId, String actorEmail) {
        User staff = userRepository.findById(userId)
                .filter(user -> user.getRole() == Role.STAFF)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Staff member not found"));
        User actor = userRepository.findByEmail(actorEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (reassignToId != null && !reassignToId.isBlank()) {
            if (reassignToId.equals(staff.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Choose someone other than the person being removed");
            }
            User target = requireAssignable(reassignToId, "The person to reassign to was not found");
            workOrderService.reassignActiveWorkOrders(staff, target);
        } else {
            workOrderService.cancelActiveWorkOrders(staff, actor);
        }
        // STUDENT has no special rights; a non-@smail account can no longer sign in at all
        staff.setRole(Role.STUDENT);
        userRepository.save(staff);
    }

    private User requireAssignable(String userId, String notFoundMessage) {
        return userRepository.findById(userId)
                .filter(user -> user.getRole() == Role.STAFF || user.getRole() == Role.ADMIN)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, notFoundMessage));
    }

    private StaffMemberView toView(User user) {
        List<WorkOrder> active = workOrderRepository.findByAssigneeAndStatusIn(user, WorkOrderService.ACTIVE);
        long overdue = active.stream().filter(WorkOrderService::isOverdue).count();
        String name = user.getFullName() != null && !user.getFullName().isBlank() ? user.getFullName() : user.getName();
        // addStaff stores the email as the name; the first sign-in replaces it with the Google name
        boolean invitePending = user.getEmail().equalsIgnoreCase(user.getName());
        return new StaffMemberView(user.getId(), user.getEmail(), name, user.getPhoneNumber(), user.getRole(),
                active.size(), overdue, invitePending);
    }
}

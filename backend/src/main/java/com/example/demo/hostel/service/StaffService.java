package com.example.demo.hostel.service;

import com.example.demo.hostel.dto.StaffMemberView;
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

    @Transactional(readOnly = true)
    public List<StaffMemberView> listStaff() {
        return userRepository.findByRoleInOrderByEmailAsc(List.of(Role.STAFF)).stream()
                .map(this::toView)
                .toList();
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
     * Removes a staff member: their active work orders are cancelled (those
     * complaints go back to PENDING, logged in history) and they lose access.
     */
    @Transactional
    public void removeStaff(String userId, String actorEmail) {
        User staff = userRepository.findById(userId)
                .filter(user -> user.getRole() == Role.STAFF)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Staff member not found"));
        User actor = userRepository.findByEmail(actorEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        workOrderService.cancelActiveWorkOrders(staff, actor);
        // STUDENT has no special rights; a non-@smail account can no longer sign in at all
        staff.setRole(Role.STUDENT);
        userRepository.save(staff);
    }

    private StaffMemberView toView(User user) {
        long active = workOrderRepository.findByAssigneeAndStatusIn(user, WorkOrderService.ACTIVE).size();
        String name = user.getFullName() != null && !user.getFullName().isBlank() ? user.getFullName() : user.getName();
        return new StaffMemberView(user.getId(), user.getEmail(), name, user.getPhoneNumber(), active);
    }
}

package com.example.demo.hostel.service;

import com.example.demo.hostel.model.LoginType;
import com.example.demo.hostel.model.Role;
import com.example.demo.hostel.model.User;
import com.example.demo.hostel.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Decides who may use HostelFix and with which role. Google proves who someone is; this
 * class applies the hostel's rules. Students need a verified smail account, admins are
 * listed in ADMIN_EMAILS, and staff are added by an admin (StaffService).
 */
@Service
public class AuthService {

    private static final String STUDENT_DOMAIN = "smail.iitm.ac.in";

    private final UserRepository userRepository;
    private final Set<String> adminEmails;

    public AuthService(
            UserRepository userRepository,
            @Value("${app.auth.admin-emails:}") String configuredAdminEmails
    ) {
        this.userRepository = userRepository;
        this.adminEmails = Arrays.stream(configuredAdminEmails.split(","))
                .map(String::trim)
                .filter(email -> !email.isEmpty())
                .map(email -> email.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * The Student Login and Staff Login tabs. Students must use a verified smail
     * account, which is created if new. Staff Login never creates anyone except
     * the admins in ADMIN_EMAILS, who would otherwise have no way in on a new
     * database; staff must already have been added by an admin. Every later
     * request is still checked by processGoogleLogin.
     */
    public User login(Jwt jwt, LoginType loginType) {
        String rawEmail = jwt.getClaimAsString("email");
        if (rawEmail == null || rawEmail.isBlank() || !Boolean.TRUE.equals(jwt.getClaim("email_verified"))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Google must verify your email address before you can sign in.");
        }

        String email = rawEmail.toLowerCase(Locale.ROOT);
        boolean isSmailAccount = isSmailAccount(email, jwt);
        boolean isStaffOrAdmin = adminEmails.contains(email) || userRepository.findByEmail(email)
                .filter(user -> user.getRole() == Role.STAFF)
                .isPresent();

        if (loginType == LoginType.ADMIN && !isStaffOrAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unauthorized staff email");
        }
        if (loginType == LoginType.STUDENT && !isSmailAccount) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, isStaffOrAdmin
                    ? "Please use Staff Login."
                    : "Students must sign in with their @smail.iitm.ac.in account.");
        }
        if (loginType == LoginType.STUDENT && isStaffOrAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Please use Staff Login.");
        }
        return processGoogleLogin(jwt);
    }

    /**
     * Runs on every request: checks the token's account, works out the role, and creates or
     * updates the user. ADMIN_EMAILS wins over a stored role, and staff keep theirs.
     *
     * @throws IllegalArgumentException if this account may not use HostelFix
     */
    public User processGoogleLogin(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Your Google account did not provide an email address.");
        }

        if (!Boolean.TRUE.equals(jwt.getClaim("email_verified"))) {
            throw new IllegalArgumentException("Google must verify your email address before you can sign in.");
        }

        email = email.toLowerCase(Locale.ROOT);
        boolean isSmailAccount = isSmailAccount(email, jwt);
        boolean isAdmin = adminEmails.contains(email);
        User user = userRepository.findByEmail(email).orElse(null);
        // Hostel staff are added by an admin (StaffService) and may use any verified
        // email; their role is kept across logins rather than recomputed
        boolean isStaff = user != null && user.getRole() == Role.STAFF;

        if (!isSmailAccount && !isAdmin && !isStaff) {
            throw new IllegalArgumentException(
                    "Use your verified @smail.iitm.ac.in student account or an approved administrator or staff account."
            );
        }

        String name = jwt.getClaimAsString("name");
        Role role = isAdmin ? Role.ADMIN : isStaff ? Role.STAFF : Role.STUDENT;
        boolean isNewUser = user == null;
        if (isNewUser) {
            user = new User();
        }
        boolean changed = isNewUser
                || !email.equals(user.getEmail())
                || user.getRole() != role
                || (name != null && !name.isBlank() && !name.equals(user.getName()));
        user.setEmail(email);
        if (name != null && !name.isBlank()) {
            user.setName(name);
        }
        user.setRole(role);
        return changed ? userRepository.save(user) : user;
    }

    private static boolean isSmailAccount(String email, Jwt jwt) {
        return email.endsWith("@" + STUDENT_DOMAIN) && STUDENT_DOMAIN.equals(jwt.getClaimAsString("hd"));
    }
}

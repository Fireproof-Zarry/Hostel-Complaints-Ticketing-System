package com.example.demo.hostel.service;

import com.example.demo.hostel.model.Role;
import com.example.demo.hostel.model.User;
import com.example.demo.hostel.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

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

    public User processGoogleLogin(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Your Google account did not provide an email address.");
        }

        email = email.toLowerCase(Locale.ROOT);
        String hostedDomain = jwt.getClaimAsString("hd");
        boolean isSmailAccount = email.endsWith("@" + STUDENT_DOMAIN)
                && STUDENT_DOMAIN.equals(hostedDomain);
        boolean isAdmin = adminEmails.contains(email);

        if (!isSmailAccount && !isAdmin) {
            throw new IllegalArgumentException(
                    "Use your verified @smail.iitm.ac.in student account or an approved administrator account."
            );
        }

        String name = jwt.getClaimAsString("name");
        Role role = isAdmin ? Role.ADMIN : Role.STUDENT;
        User user = userRepository.findByEmail(email).orElse(null);
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
}

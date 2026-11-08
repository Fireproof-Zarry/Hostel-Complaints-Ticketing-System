package com.example.demo.hostel.service;

import com.example.demo.hostel.model.Role;
import com.example.demo.hostel.model.User;
import com.example.demo.hostel.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Test
    void smailStudentIsCreatedAsStudent() {
        when(userRepository.findByEmail("student@smail.iitm.ac.in")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User user = new AuthService(userRepository, "", email -> true).processGoogleLogin(jwt(
                "student@smail.iitm.ac.in", "Student", "smail.iitm.ac.in", true
        ));

        assertEquals(Role.STUDENT, user.getRole());
        verify(userRepository).save(user);
    }

    @Test
    void allowlistedSmailAccountIsAdmin() {
        User existingUser = new User();
        existingUser.setEmail("cs23b051@smail.iitm.ac.in");
        existingUser.setName("Administrator");
        existingUser.setRole(Role.STUDENT);
        when(userRepository.findByEmail(existingUser.getEmail())).thenReturn(Optional.of(existingUser));
        when(userRepository.save(existingUser)).thenReturn(existingUser);

        User user = new AuthService(userRepository, existingUser.getEmail(), email -> true).processGoogleLogin(jwt(
                existingUser.getEmail(), "Administrator", "smail.iitm.ac.in", true
        ));

        assertEquals(Role.ADMIN, user.getRole());
        verify(userRepository).save(existingUser);
    }

    @Test
    void allowlistedPersonalEmailIsCreatedAsAdmin() {
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User user = new AuthService(userRepository, "admin@example.com", email -> true).processGoogleLogin(jwt(
                "admin@example.com", "Admin", null, true
        ));

        assertEquals(Role.ADMIN, user.getRole());
        verify(userRepository).save(user);
    }

    @Test
    void otherPersonalEmailIsRejected() {
        // Looked up to see whether an admin added it as staff; it was not
        when(userRepository.findByEmail("someone@gmail.com")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                new AuthService(userRepository, "", email -> true).processGoogleLogin(jwt(
                        "someone@gmail.com", "Someone", null, true
                ))
        );
        verify(userRepository, never()).save(any());
    }

    @Test
    void unverifiedSmailEmailIsRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                new AuthService(userRepository, "", email -> true).processGoogleLogin(jwt(
                        "student@smail.iitm.ac.in", "Student", "smail.iitm.ac.in", false
                ))
        );
        verifyNoInteractions(userRepository);
    }

    @Test
    void missingEmailVerifiedClaimIsRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                new AuthService(userRepository, "", email -> true).processGoogleLogin(jwt(
                        "student@smail.iitm.ac.in", "Student", "smail.iitm.ac.in", null
                ))
        );
        verifyNoInteractions(userRepository);
    }

    @Test
    void unverifiedConfiguredAdminEmailIsRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                new AuthService(userRepository, "admin@example.com", email -> true).processGoogleLogin(jwt(
                        "admin@example.com", "Admin", null, false
                ))
        );
        verifyNoInteractions(userRepository);
    }

    @Test
    void staffAddedByAnAdminCanSignInWithAPersonalEmailAndKeepTheirRole() {
        User staff = new User();
        staff.setEmail("warden@gmail.com");
        staff.setName("warden@gmail.com");
        staff.setRole(Role.STAFF);
        when(userRepository.findByEmail("warden@gmail.com")).thenReturn(Optional.of(staff));
        when(userRepository.save(staff)).thenReturn(staff);

        User user = new AuthService(userRepository, "", email -> true).processGoogleLogin(jwt(
                "warden@gmail.com", "Hostel Warden", null, true
        ));

        assertEquals(Role.STAFF, user.getRole());
        // The placeholder name set when the admin added them is replaced on first login
        assertEquals("Hostel Warden", user.getName());
    }

    @Test
    void adminAllowlistWinsOverStaff() {
        User staff = new User();
        staff.setEmail("warden@gmail.com");
        staff.setName("Warden");
        staff.setRole(Role.STAFF);
        when(userRepository.findByEmail("warden@gmail.com")).thenReturn(Optional.of(staff));
        when(userRepository.save(staff)).thenReturn(staff);

        User user = new AuthService(userRepository, "warden@gmail.com", email -> true).processGoogleLogin(jwt(
                "warden@gmail.com", "Warden", null, true
        ));

        assertEquals(Role.ADMIN, user.getRole());
    }

    @Test
    void removedStaffWithAPersonalEmailCanNoLongerSignIn() {
        User removed = new User();
        removed.setEmail("former-warden@gmail.com");
        removed.setName("Former Warden");
        removed.setRole(Role.STUDENT);
        when(userRepository.findByEmail("former-warden@gmail.com")).thenReturn(Optional.of(removed));

        assertThrows(IllegalArgumentException.class, () ->
                new AuthService(userRepository, "", email -> true).processGoogleLogin(jwt(
                        "former-warden@gmail.com", "Former Warden", null, true
                ))
        );
        verify(userRepository, never()).save(any());
    }

    private Jwt jwt(String email, String name, String hostedDomain, Boolean emailVerified) {
        Jwt.Builder builder = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .claim("email", email)
                .claim("name", name);
        if (hostedDomain != null) {
            builder.claim("hd", hostedDomain);
        }
        if (emailVerified != null) {
            builder.claim("email_verified", emailVerified);
        }
        return builder.build();
    }
}

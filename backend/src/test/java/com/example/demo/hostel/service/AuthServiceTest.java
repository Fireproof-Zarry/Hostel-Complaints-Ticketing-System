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

        User user = new AuthService(userRepository, "").processGoogleLogin(jwt(
                "student@smail.iitm.ac.in", "Student", "smail.iitm.ac.in"
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

        User user = new AuthService(userRepository, existingUser.getEmail()).processGoogleLogin(jwt(
                existingUser.getEmail(), "Administrator", "smail.iitm.ac.in"
        ));

        assertEquals(Role.ADMIN, user.getRole());
        verify(userRepository).save(existingUser);
    }

    @Test
    void allowlistedPersonalEmailIsCreatedAsAdmin() {
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User user = new AuthService(userRepository, "admin@example.com").processGoogleLogin(jwt(
                "admin@example.com", "Admin", null
        ));

        assertEquals(Role.ADMIN, user.getRole());
        verify(userRepository).save(user);
    }

    @Test
    void otherPersonalEmailIsRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                new AuthService(userRepository, "").processGoogleLogin(jwt(
                        "someone@gmail.com", "Someone", null
                ))
        );
        verifyNoInteractions(userRepository);
    }

    private Jwt jwt(String email, String name, String hostedDomain) {
        Jwt.Builder builder = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .claim("email", email)
                .claim("name", name);
        if (hostedDomain != null) {
            builder.claim("hd", hostedDomain);
        }
        return builder.build();
    }
}

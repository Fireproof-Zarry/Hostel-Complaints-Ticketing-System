package com.example.demo.hostel.service;

import com.example.demo.hostel.model.User;
import com.example.demo.hostel.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private User student;

    @BeforeEach
    void setUp() {
        student = new User();
        student.setId("u1");
        student.setEmail("student@smail.iitm.ac.in");
    }

    @Test
    void savesTrimmedTokenForOwnAccount() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(student));
        when(userRepository.findByFcmToken("device-token")).thenReturn(List.of());

        userService.updateFcmToken("u1", "Student@smail.iitm.ac.in", "  device-token ");

        assertThat(student.getFcmToken()).isEqualTo("device-token");
        verify(userRepository).save(student);
    }

    @Test
    void removesSameTokenFromOtherAccounts() {
        User previousOwner = new User();
        previousOwner.setId("u2");
        previousOwner.setFcmToken("device-token");
        when(userRepository.findById("u1")).thenReturn(Optional.of(student));
        when(userRepository.findByFcmToken("device-token")).thenReturn(List.of(previousOwner));

        userService.updateFcmToken("u1", "student@smail.iitm.ac.in", "device-token");

        assertThat(previousOwner.getFcmToken()).isNull();
        assertThat(student.getFcmToken()).isEqualTo("device-token");
    }

    @Test
    void rejectsUpdatingAnotherUsersToken() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(student));

        assertStatus(() -> userService.updateFcmToken("u1", "attacker@smail.iitm.ac.in", "device-token"),
                HttpStatus.FORBIDDEN);
        verify(userRepository, never()).save(any());
    }

    @Test
    void rejectsBlankToken() {
        assertStatus(() -> userService.updateFcmToken("u1", "student@smail.iitm.ac.in", " "), HttpStatus.BAD_REQUEST);
    }

    @Test
    void unknownUserIsNotFound() {
        when(userRepository.findById("missing")).thenReturn(Optional.empty());

        assertStatus(() -> userService.updateFcmToken("missing", "student@smail.iitm.ac.in", "t"), HttpStatus.NOT_FOUND);
    }

    @Test
    void clearsOwnToken() {
        student.setFcmToken("device-token");
        when(userRepository.findById("u1")).thenReturn(Optional.of(student));

        userService.clearFcmToken("u1", "student@smail.iitm.ac.in");

        assertThat(student.getFcmToken()).isNull();
        verify(userRepository).save(student);
    }

    private static void assertStatus(Runnable call, HttpStatus expected) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(expected));
    }
}

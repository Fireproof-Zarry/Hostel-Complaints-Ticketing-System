package com.example.demo.hostel.service;

import com.example.demo.hostel.dto.ProfileUpdateRequest;
import com.example.demo.hostel.model.Role;
import com.example.demo.hostel.model.User;
import com.example.demo.hostel.repository.UserRepository;
import com.example.demo.hostel.validation.RoomLocationValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
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

    @Spy
    private RoomLocationValidator roomLocationValidator = new RoomLocationValidator();

    @InjectMocks
    private UserService userService;

    private User student;

    @BeforeEach
    void setUp() {
        student = new User();
        student.setId("u1");
        student.setEmail("student@smail.iitm.ac.in");
        student.setRole(Role.STUDENT);
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

    @Test
    void studentProfileIsTrimmedNormalizedAndSaved() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(student));
        when(userRepository.save(student)).thenReturn(student);

        User saved = userService.updateProfile("u1", "student@smail.iitm.ac.in",
                new ProfileUpdateRequest(" Ragini Z ", "+91 98765 43210", " cs23b051 ", " 312 "));

        assertThat(saved.getFullName()).isEqualTo("Ragini Z");
        assertThat(saved.getPhoneNumber()).isEqualTo("+91 98765 43210");
        assertThat(saved.getRollNumber()).isEqualTo("CS23B051");
        assertThat(saved.getRoomNumber()).isEqualTo("312");
    }

    @Test
    void blankProfileFieldsAreCleared() {
        student.setPhoneNumber("9876543210");
        student.setRoomNumber("312");
        when(userRepository.findById("u1")).thenReturn(Optional.of(student));
        when(userRepository.save(student)).thenReturn(student);

        userService.updateProfile("u1", "student@smail.iitm.ac.in", new ProfileUpdateRequest("Name", " ", null, ""));

        assertThat(student.getPhoneNumber()).isNull();
        assertThat(student.getRoomNumber()).isNull();
    }

    @Test
    void adminProfileNeverStoresRollOrRoom() {
        User admin = new User();
        admin.setId("a1");
        admin.setEmail("warden@smail.iitm.ac.in");
        admin.setRole(Role.ADMIN);
        when(userRepository.findById("a1")).thenReturn(Optional.of(admin));
        when(userRepository.save(admin)).thenReturn(admin);

        userService.updateProfile("a1", "warden@smail.iitm.ac.in",
                new ProfileUpdateRequest("Warden", "9876543210", "CS23B051", "312"));

        assertThat(admin.getFullName()).isEqualTo("Warden");
        assertThat(admin.getRollNumber()).isNull();
        assertThat(admin.getRoomNumber()).isNull();
    }

    @Test
    void rejectsInvalidProfileValues() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(student));
        String email = "student@smail.iitm.ac.in";

        assertStatus(() -> userService.updateProfile("u1", email, new ProfileUpdateRequest(null, null, null, "999")),
                HttpStatus.BAD_REQUEST);
        assertStatus(() -> userService.updateProfile("u1", email, new ProfileUpdateRequest(null, null, null, "31")),
                HttpStatus.BAD_REQUEST);
        assertStatus(() -> userService.updateProfile("u1", email, new ProfileUpdateRequest(null, "12345", null, null)),
                HttpStatus.BAD_REQUEST);
        assertStatus(() -> userService.updateProfile("u1", email, new ProfileUpdateRequest(null, "call me", null, null)),
                HttpStatus.BAD_REQUEST);
        assertStatus(() -> userService.updateProfile("u1", email, new ProfileUpdateRequest(null, null, "CS-23", null)),
                HttpStatus.BAD_REQUEST);
        assertStatus(() -> userService.updateProfile("u1", email, new ProfileUpdateRequest("x".repeat(101), null, null, null)),
                HttpStatus.BAD_REQUEST);
        verify(userRepository, never()).save(any());
    }

    @Test
    void cannotUpdateAnotherUsersProfile() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(student));

        assertStatus(() -> userService.updateProfile("u1", "attacker@smail.iitm.ac.in",
                new ProfileUpdateRequest("Hacked", null, null, null)), HttpStatus.FORBIDDEN);
        assertThat(student.getFullName()).isNull();
    }

    private static void assertStatus(Runnable call, HttpStatus expected) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(expected));
    }
}

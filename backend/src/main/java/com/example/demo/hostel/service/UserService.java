package com.example.demo.hostel.service;

import com.example.demo.hostel.dto.ProfileUpdateRequest;
import com.example.demo.hostel.model.Role;
import com.example.demo.hostel.model.User;
import com.example.demo.hostel.repository.UserRepository;
import com.example.demo.hostel.validation.RoomLocationValidator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

@Service
public class UserService {

    private static final int MAX_NAME_LENGTH = 100;

    private final UserRepository userRepository;
    private final RoomLocationValidator roomLocationValidator;

    public UserService(UserRepository userRepository, RoomLocationValidator roomLocationValidator) {
        this.userRepository = userRepository;
        this.roomLocationValidator = roomLocationValidator;
    }

    /**
     * Updates the signed-in user's profile. Roll and room numbers only apply
     * to students; for admins they are always stored as null.
     */
    @Transactional
    public User updateProfile(String userId, String requesterEmail, ProfileUpdateRequest request) {
        User user = findOwnUser(userId, requesterEmail);

        String fullName = trimToNull(request.fullName());
        if (fullName != null && fullName.length() > MAX_NAME_LENGTH) {
            throw badRequest("Full name must be at most " + MAX_NAME_LENGTH + " characters");
        }

        String phoneNumber = trimToNull(request.phoneNumber());
        if (phoneNumber != null && !isValidPhoneNumber(phoneNumber)) {
            throw badRequest("Enter a valid phone number (10-15 digits, optional leading +)");
        }

        String rollNumber = null;
        String roomNumber = null;
        if (user.getRole() == Role.STUDENT) {
            rollNumber = trimToNull(request.rollNumber());
            if (rollNumber != null) {
                rollNumber = rollNumber.toUpperCase(Locale.ROOT);
                if (!rollNumber.matches("[A-Z0-9]{4,20}")) {
                    throw badRequest("Roll number must be 4-20 letters or digits");
                }
            }
            roomNumber = trimToNull(request.roomNumber());
            if (roomNumber != null && roomLocationValidator.floorForRoom(roomNumber) == null) {
                throw badRequest("Room number must be a 3-digit hostel room from 101 to 799");
            }
        }

        user.setFullName(fullName);
        user.setPhoneNumber(phoneNumber);
        user.setRollNumber(rollNumber);
        user.setRoomNumber(roomNumber);
        return userRepository.save(user);
    }

    /**
     * Stores the device token for the signed-in user. A browser/device has one
     * token, so it is removed from any other account that previously used it
     * (e.g. a shared laptop) to stop that account's alerts reaching this device.
     */
    @Transactional
    public void updateFcmToken(String userId, String requesterEmail, String token) {
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "FCM token is required");
        }
        String trimmed = token.trim();
        User user = findOwnUser(userId, requesterEmail);

        for (User other : userRepository.findByFcmToken(trimmed)) {
            if (!other.getId().equals(user.getId())) {
                other.setFcmToken(null);
                userRepository.save(other);
            }
        }

        user.setFcmToken(trimmed);
        userRepository.save(user);
    }

    /** Removes the device token, e.g. on logout. */
    @Transactional
    public void clearFcmToken(String userId, String requesterEmail) {
        User user = findOwnUser(userId, requesterEmail);
        user.setFcmToken(null);
        userRepository.save(user);
    }

    private static boolean isValidPhoneNumber(String phoneNumber) {
        if (!phoneNumber.matches("\\+?[0-9][0-9 -]*[0-9]")) {
            return false;
        }
        int digits = phoneNumber.replaceAll("[^0-9]", "").length();
        return digits >= 10 && digits <= 15;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }

    // Users may only manage their own token and profile
    private User findOwnUser(String userId, String requesterEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (requesterEmail == null || !requesterEmail.equalsIgnoreCase(user.getEmail())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only update your own account");
        }
        return user;
    }
}

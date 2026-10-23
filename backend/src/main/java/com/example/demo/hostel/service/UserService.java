package com.example.demo.hostel.service;

import com.example.demo.hostel.model.User;
import com.example.demo.hostel.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
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

    // Users may only manage their own token
    private User findOwnUser(String userId, String requesterEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (requesterEmail == null || !requesterEmail.equalsIgnoreCase(user.getEmail())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only update your own device token");
        }
        return user;
    }
}

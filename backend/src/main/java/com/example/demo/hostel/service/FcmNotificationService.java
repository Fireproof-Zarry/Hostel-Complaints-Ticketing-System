package com.example.demo.hostel.service;

import com.example.demo.hostel.model.User;
import com.example.demo.hostel.repository.UserRepository;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class FcmNotificationService {

    private static final Logger log = LoggerFactory.getLogger(FcmNotificationService.class);

    private final ObjectProvider<FirebaseMessaging> firebaseMessaging;
    private final UserRepository userRepository;

    public FcmNotificationService(ObjectProvider<FirebaseMessaging> firebaseMessaging, UserRepository userRepository) {
        this.firebaseMessaging = firebaseMessaging;
        this.userRepository = userRepository;
    }

    /**
     * Sends a push notification on a background thread. Failures are logged
     * and never propagate to the caller.
     */
    @Async
    public void sendStatusUpdateNotification(String targetToken, String complaintTitle, String newStatus) {
        FirebaseMessaging messaging = firebaseMessaging.getIfAvailable();
        if (messaging == null) {
            log.debug("Firebase is not configured; skipping status notification");
            return;
        }
        if (targetToken == null || targetToken.isBlank()) {
            return;
        }

        String readableStatus = formatStatus(newStatus);
        Message message = Message.builder()
                .setToken(targetToken)
                .setNotification(Notification.builder()
                        .setTitle("Complaint status updated")
                        .setBody("\"" + complaintTitle + "\" is now " + readableStatus + ".")
                        .build())
                .putData("status", newStatus == null ? "" : newStatus)
                .build();

        try {
            String messageId = messaging.send(message);
            log.info("Sent status notification {} (status {})", messageId, newStatus);
        } catch (FirebaseMessagingException e) {
            log.warn("Could not send status notification to token {}: {} ({})",
                    abbreviate(targetToken), e.getMessage(), e.getMessagingErrorCode());
            if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
                clearToken(targetToken);
            }
        } catch (RuntimeException e) {
            log.error("Unexpected error while sending status notification", e);
        }
    }

    // The device uninstalled the app or the token expired; stop sending to it
    private void clearToken(String token) {
        for (User user : userRepository.findByFcmToken(token)) {
            user.setFcmToken(null);
            userRepository.save(user);
        }
    }

    private static String formatStatus(String status) {
        if (status == null || status.isBlank()) return "updated";
        String words = status.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    private static String abbreviate(String token) {
        return token.length() <= 10 ? token : token.substring(0, 10) + "…";
    }
}

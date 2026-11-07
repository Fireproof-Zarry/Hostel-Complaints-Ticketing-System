package com.example.demo.hostel.service;

import com.example.demo.hostel.model.User;
import com.example.demo.hostel.repository.UserRepository;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * Push notifications through Firebase Cloud Messaging. Does nothing if Firebase is not
 * configured, and sending can never fail the request that triggered it.
 */
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
     * Tells someone a complaint was assigned to them, on a background thread.
     * Failures are logged and never propagate to the caller.
     */
    @Async
    public void sendAssignmentNotification(String targetToken, String body) {
        FirebaseMessaging messaging = firebaseMessaging.getIfAvailable();
        if (messaging == null) {
            log.warn("Firebase is not configured; skipping assignment notification");
            return;
        }
        if (targetToken == null || targetToken.isBlank()) {
            log.info("Skipping assignment notification: the assignee has no registered device");
            return;
        }

        Message message = Message.builder()
                .setToken(targetToken)
                .setNotification(Notification.builder()
                        .setTitle("New complaint assigned")
                        .setBody(body)
                        .build())
                .build();

        try {
            String messageId = messaging.send(message);
            log.info("Sent assignment notification {}", messageId);
        } catch (FirebaseMessagingException e) {
            log.warn("Could not send assignment notification to token {}: {} ({})",
                    abbreviate(targetToken), e.getMessage(), e.getMessagingErrorCode());
            if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
                clearToken(targetToken);
            }
        } catch (RuntimeException e) {
            log.error("Unexpected error while sending assignment notification", e);
        }
    }

    /**
     * Sends a push notification on a background thread. Failures are logged
     * and never propagate to the caller. {@code link} is the page a click opens
     * (see complaintUrl); null opens the app's home page.
     */
    @Async
    public void sendStatusUpdateNotification(String targetToken, String complaintTitle, String newStatus, String link) {
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
                .putAllData(link == null ? java.util.Map.of() : java.util.Map.of("url", link))
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

    /**
     * Tells every admin about a nudge in one multicast, on a background thread.
     * Failures are logged and never propagate to the caller.
     */
    @Async
    public void sendNudgeNotification(List<String> adminTokens, Long complaintId, String complaintTitle) {
        FirebaseMessaging messaging = firebaseMessaging.getIfAvailable();
        if (messaging == null) {
            log.debug("Firebase is not configured; skipping nudge notification");
            return;
        }
        List<String> tokens = adminTokens.stream()
                .filter(token -> token != null && !token.isBlank())
                .distinct()
                .toList();
        if (tokens.isEmpty()) {
            log.info("No admin has push notifications on; nudge on complaint {} not pushed", complaintId);
            return;
        }

        MulticastMessage message = MulticastMessage.builder()
                .addAllTokens(tokens)
                .setNotification(Notification.builder()
                        .setTitle("Complaint nudged")
                        .setBody("#" + complaintId + " \"" + complaintTitle + "\" has been waiting over a week.")
                        .build())
                .putData("complaintId", String.valueOf(complaintId))
                .putData("url", nudgeUrl(complaintId))
                .build();

        try {
            BatchResponse batch = messaging.sendEachForMulticast(message);
            log.info("Sent nudge notification for complaint {}: {} of {} delivered",
                    complaintId, batch.getSuccessCount(), tokens.size());
            List<SendResponse> responses = batch.getResponses();
            for (int i = 0; i < responses.size(); i++) {
                FirebaseMessagingException error = responses.get(i).getException();
                if (error != null) {
                    log.warn("Could not send nudge notification to token {}: {} ({})",
                            abbreviate(tokens.get(i)), error.getMessage(), error.getMessagingErrorCode());
                    if (error.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
                        clearToken(tokens.get(i));
                    }
                }
            }
        } catch (FirebaseMessagingException e) {
            log.warn("Could not send nudge notification: {} ({})", e.getMessage(), e.getMessagingErrorCode());
        } catch (RuntimeException e) {
            log.error("Unexpected error while sending nudge notification", e);
        }
    }

    // The device uninstalled the app or the token expired; stop sending to it
    private void clearToken(String token) {
        for (User user : userRepository.findByFcmToken(token)) {
            user.setFcmToken(null);
            userRepository.save(user);
        }
    }

    // Where clicking a status update takes the student: My Complaints, scrolled to it
    static String complaintUrl(Long complaintId) {
        return "/student?complaint=" + complaintId;
    }

    // Where clicking a nudge takes the admin; the page highlights this complaint
    static String nudgeUrl(Long complaintId) {
        return "/admin?complaint=" + complaintId;
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

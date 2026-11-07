package com.example.demo.hostel.service;

import com.example.demo.hostel.model.User;
import com.example.demo.hostel.repository.UserRepository;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.SendResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FcmNotificationServiceTest {

    private final FirebaseMessaging messaging = mock(FirebaseMessaging.class);
    private final UserRepository userRepository = mock(UserRepository.class);

    @SuppressWarnings("unchecked")
    private FcmNotificationService service(FirebaseMessaging available) {
        ObjectProvider<FirebaseMessaging> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(available);
        return new FcmNotificationService(provider, userRepository);
    }

    @Test
    void sendsMessageWhenFirebaseIsConfigured() throws Exception {
        when(messaging.send(any(Message.class))).thenReturn("projects/p/messages/1");

        service(messaging).sendStatusUpdateNotification("token-123", "Leaking tap", "IN_PROGRESS", "/student?complaint=7");

        verify(messaging).send(any(Message.class));
    }

    @Test
    void sendsAssignmentNotifications() throws Exception {
        when(messaging.send(any(Message.class))).thenReturn("projects/p/messages/2");

        service(messaging).sendAssignmentNotification("token-123", "\"Leaking tap\" was assigned to you.");

        verify(messaging).send(any(Message.class));
    }

    @Test
    void assignmentNotificationsSkipMissingTokensAndUnconfiguredFirebase() throws Exception {
        service(messaging).sendAssignmentNotification(null, "body");
        service(messaging).sendAssignmentNotification(" ", "body");

        verify(messaging, never()).send(any(Message.class));
        assertThatCode(() -> service(null).sendAssignmentNotification("token-123", "body"))
                .doesNotThrowAnyException();
    }

    @Test
    void clearsATokenThatFailsOnAnAssignmentNotification() throws Exception {
        FirebaseMessagingException error = mock(FirebaseMessagingException.class);
        when(error.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);
        when(messaging.send(any(Message.class))).thenThrow(error);

        User user = new User();
        user.setFcmToken("stale-token");
        when(userRepository.findByFcmToken("stale-token")).thenReturn(List.of(user));

        service(messaging).sendAssignmentNotification("stale-token", "body");

        org.assertj.core.api.Assertions.assertThat(user.getFcmToken()).isNull();
    }

    @Test
    void skipsQuietlyWhenFirebaseIsNotConfigured() {
        assertThatCode(() -> service(null).sendStatusUpdateNotification("token-123", "Leaking tap", "RESOLVED", "/student?complaint=7"))
                .doesNotThrowAnyException();
    }

    @Test
    void skipsBlankToken() throws Exception {
        service(messaging).sendStatusUpdateNotification(" ", "Leaking tap", "RESOLVED", "/student?complaint=7");

        verify(messaging, never()).send(any(Message.class));
    }

    @Test
    void logsFirebaseErrorsWithoutThrowing() throws Exception {
        FirebaseMessagingException error = mock(FirebaseMessagingException.class);
        when(error.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNAVAILABLE);
        when(messaging.send(any(Message.class))).thenThrow(error);

        assertThatCode(() -> service(messaging).sendStatusUpdateNotification("token-123", "Leaking tap", "RESOLVED", "/student?complaint=7"))
                .doesNotThrowAnyException();
        verifyNoInteractions(userRepository);
    }

    @Test
    void clearsTokenThatFirebaseReportsAsUnregistered() throws Exception {
        FirebaseMessagingException error = mock(FirebaseMessagingException.class);
        when(error.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);
        when(messaging.send(any(Message.class))).thenThrow(error);

        User user = new User();
        user.setFcmToken("stale-token");
        when(userRepository.findByFcmToken("stale-token")).thenReturn(List.of(user));

        service(messaging).sendStatusUpdateNotification("stale-token", "Leaking tap", "RESOLVED", "/student?complaint=7");

        verify(userRepository).save(user);
        org.assertj.core.api.Assertions.assertThat(user.getFcmToken()).isNull();
    }

    @Test
    void unexpectedRuntimeErrorsDoNotEscape() throws Exception {
        when(messaging.send(any(Message.class))).thenThrow(new IllegalStateException("boom"));

        assertThatCode(() -> service(messaging).sendStatusUpdateNotification("token-123", "Leaking tap", "RESOLVED", "/student?complaint=7"))
                .doesNotThrowAnyException();
    }

    @Test
    void nudgeGoesToAllAdminsInOneMulticast() throws Exception {
        BatchResponse batch = mock(BatchResponse.class);
        when(batch.getResponses()).thenReturn(List.of());
        when(messaging.sendEachForMulticast(any(MulticastMessage.class))).thenReturn(batch);

        service(messaging).sendNudgeNotification(List.of("admin-1", "admin-2", "admin-1", " "), 7L, "Leaking tap");

        verify(messaging, times(1)).sendEachForMulticast(any(MulticastMessage.class));
        verify(messaging, never()).send(any(Message.class));
    }

    @Test
    void statusUpdateWithoutALinkStillSends() throws Exception {
        when(messaging.send(any(Message.class))).thenReturn("projects/p/messages/1");

        service(messaging).sendStatusUpdateNotification("token-123", "Leaking tap", "RESOLVED", null);

        verify(messaging).send(any(Message.class));
    }

    @Test
    void statusUpdateLinksToTheComplaintInMyComplaints() {
        org.assertj.core.api.Assertions.assertThat(FcmNotificationService.complaintUrl(7L))
                .isEqualTo("/student?complaint=7");
    }

    @Test
    void nudgeLinksToTheComplaintOnTheAdminPage() {
        org.assertj.core.api.Assertions.assertThat(FcmNotificationService.nudgeUrl(7L))
                .isEqualTo("/admin?complaint=7");
    }

    @Test
    void nudgeWithNoAdminTokensSendsNothing() throws Exception {
        service(messaging).sendNudgeNotification(List.of(), 7L, "Leaking tap");

        verify(messaging, never()).sendEachForMulticast(any(MulticastMessage.class));
    }

    @Test
    void nudgeSkipsQuietlyWhenFirebaseIsNotConfigured() {
        assertThatCode(() -> service(null).sendNudgeNotification(List.of("admin-1"), 7L, "Leaking tap"))
                .doesNotThrowAnyException();
    }

    @Test
    void nudgeClearsOnlyTheTokenFirebaseReportsAsUnregistered() throws Exception {
        FirebaseMessagingException error = mock(FirebaseMessagingException.class);
        when(error.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);
        SendResponse delivered = mock(SendResponse.class);
        SendResponse failed = mock(SendResponse.class);
        when(failed.getException()).thenReturn(error);
        BatchResponse batch = mock(BatchResponse.class);
        when(batch.getResponses()).thenReturn(List.of(delivered, failed));
        when(messaging.sendEachForMulticast(any(MulticastMessage.class))).thenReturn(batch);

        User stale = new User();
        stale.setFcmToken("stale-token");
        when(userRepository.findByFcmToken("stale-token")).thenReturn(List.of(stale));

        service(messaging).sendNudgeNotification(List.of("good-token", "stale-token"), 7L, "Leaking tap");

        verify(userRepository).save(stale);
        verify(userRepository, never()).findByFcmToken("good-token");
        org.assertj.core.api.Assertions.assertThat(stale.getFcmToken()).isNull();
    }
}

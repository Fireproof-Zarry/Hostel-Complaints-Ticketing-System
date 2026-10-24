package com.example.demo.hostel.service;

import com.example.demo.hostel.model.User;
import com.example.demo.hostel.repository.UserRepository;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

        service(messaging).sendStatusUpdateNotification("token-123", "Leaking tap", "IN_PROGRESS");

        verify(messaging).send(any(Message.class));
    }

    @Test
    void skipsQuietlyWhenFirebaseIsNotConfigured() {
        assertThatCode(() -> service(null).sendStatusUpdateNotification("token-123", "Leaking tap", "RESOLVED"))
                .doesNotThrowAnyException();
    }

    @Test
    void skipsBlankToken() throws Exception {
        service(messaging).sendStatusUpdateNotification(" ", "Leaking tap", "RESOLVED");

        verify(messaging, never()).send(any(Message.class));
    }

    @Test
    void logsFirebaseErrorsWithoutThrowing() throws Exception {
        FirebaseMessagingException error = mock(FirebaseMessagingException.class);
        when(error.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNAVAILABLE);
        when(messaging.send(any(Message.class))).thenThrow(error);

        assertThatCode(() -> service(messaging).sendStatusUpdateNotification("token-123", "Leaking tap", "RESOLVED"))
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

        service(messaging).sendStatusUpdateNotification("stale-token", "Leaking tap", "RESOLVED");

        verify(userRepository).save(user);
        org.assertj.core.api.Assertions.assertThat(user.getFcmToken()).isNull();
    }

    @Test
    void unexpectedRuntimeErrorsDoNotEscape() throws Exception {
        when(messaging.send(any(Message.class))).thenThrow(new IllegalStateException("boom"));

        assertThatCode(() -> service(messaging).sendStatusUpdateNotification("token-123", "Leaking tap", "RESOLVED"))
                .doesNotThrowAnyException();
    }
}

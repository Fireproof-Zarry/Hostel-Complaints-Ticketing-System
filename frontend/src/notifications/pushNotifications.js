
import { getToken, onMessage } from 'firebase/messaging';
import { messagingPromise } from '../firebase';
import { apiFetch, getToken as getSessionToken } from '../auth/session';

const apiBaseUrl = (import.meta.env.VITE_API_URL || '/api').replace(/\/$/, '');

// The registration for the current login, shared by every caller so StrictMode
// re-runs and page remounts send one request. The key includes the session
// token, so each new login refreshes the FCM token, even for the same student.
let currentRegistration = null;

export function getNotificationPermission() {
  return 'Notification' in window ? Notification.permission : 'unsupported';
}

// Makes the next registration contact Firebase and the backend again.
export function forgetFcmRegistration() {
  currentRegistration = null;
}

async function registerFcmToken(userId) {
  if (!userId) {
    throw new Error('User ID is required.');
  }

  if (!('serviceWorker' in navigator)) {
    throw new Error('This browser does not support service workers.');
  }

  const vapidKey = import.meta.env.VITE_FIREBASE_VAPID_KEY;

  if (!vapidKey) {
    throw new Error('Firebase VAPID key is not configured.');
  }

  const messaging = await messagingPromise;

  if (!messaging) {
    throw new Error('Firebase Messaging is not supported in this browser.');
  }

  // Firebase registers /firebase-messaging-sw.js in its own scope, separate from
  // the PWA service worker on '/', and waits for it to activate.
  const fcmToken = await getToken(messaging, { vapidKey });

  if (!fcmToken) {
    throw new Error('Could not generate an FCM token.');
  }

  const response = await apiFetch(
    `${apiBaseUrl}/users/${userId}/fcm-token`,
    {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ token: fcmToken }),
    }
  );

  if (!response.ok) {
    throw new Error(`Failed to register FCM token: ${response.status}`);
  }

  console.log('[FCM] Token registered successfully.');

  return fcmToken;
}

// Called when the student explicitly enables notifications.
export async function enablePushNotifications(userId) {
  if (!userId) {
    throw new Error('User ID is required.');
  }

  if (!('Notification' in window)) {
    throw new Error('This browser does not support notifications.');
  }

  // Done before the prompt: closing it can refocus the tab and start the
  // automatic registration, which must not be discarded afterwards.
  forgetFcmRegistration();

  const permission = await Notification.requestPermission();

  if (permission === 'denied') {
    throw new Error(
      'Notification permission was denied. Please enable it in your browser settings.'
    );
  }

  if (permission !== 'granted') {
    throw new Error('Notification permission was not granted.');
  }

  return registerExistingPermission(userId);
}

// Called on subsequent logins when permission is already granted.
export async function registerExistingPermission(userId) {
  if (!('Notification' in window)) {
    throw new Error('This browser does not support notifications.');
  }

  if (Notification.permission !== 'granted') {
    throw new Error('Notification permission has not been granted.');
  }

  const key = `${userId}:${getSessionToken()}`;

  if (currentRegistration?.key !== key) {
    currentRegistration = { key, promise: registerFcmToken(userId) };
  }

  return currentRegistration.promise;
}

// Listen for notifications while the Student Portal is open.
export async function listenForForegroundMessages(callback) {
  console.log('[FCM] Initializing foreground listener...');

  const messaging = await messagingPromise;

  if (!messaging) {
    console.error('[FCM] Messaging is not supported.');
    return () => {};
  }

  console.log('[FCM] Foreground listener registered.');

  return onMessage(messaging, (payload) => {
    console.log('[FCM] Foreground message received:', payload);

    const title = payload.notification?.title || 'HostelFix';
    const body =
      payload.notification?.body ||
      'Your complaint status has been updated.';

    callback({ title, body, payload });
  });
}

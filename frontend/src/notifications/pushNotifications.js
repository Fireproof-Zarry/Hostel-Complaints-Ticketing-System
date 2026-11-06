
import { getToken, onMessage } from 'firebase/messaging';
import { messagingPromise } from '../firebase';
import { apiFetch, getToken as getSessionToken } from '../auth/session';

const apiBaseUrl = (import.meta.env.VITE_API_URL || '/api').replace(/\/$/, '');

// The registration for the current login, shared by every caller so StrictMode
// re-runs and page remounts send one request. The key includes the session
// token, so each new login refreshes the FCM token, even for the same student.
let currentRegistration = null;

// Logout must not hang on a slow network.
const MAX_LOGOUT_WAIT_MS = 3000;

export function getNotificationPermission() {
  return 'Notification' in window ? Notification.permission : 'unsupported';
}

// The student's choice for this browser. It outlives logout, so logging in
// again does not turn notifications back on.
function turnedOffKey(userId) {
  return `hostelfix.notificationsOff.${userId}`;
}

export function areNotificationsTurnedOff(userId) {
  return localStorage.getItem(turnedOffKey(userId)) === 'true';
}

export function setNotificationsTurnedOff(userId, turnedOff) {
  if (turnedOff) {
    localStorage.setItem(turnedOffKey(userId), 'true');
  } else {
    localStorage.removeItem(turnedOffKey(userId));
  }
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
    currentRegistration = { key, userId, promise: registerFcmToken(userId) };
  }

  return currentRegistration.promise;
}

async function deleteFcmToken(registration) {
  // A registration still in progress must not restore the token afterwards.
  await registration?.promise.catch(() => {});

  // The checks stop this from outliving the session if logout stopped waiting.
  if (!getSessionToken()) return;

  let userId = registration?.userId;

  if (!userId) {
    const response = await apiFetch(`${apiBaseUrl}/auth/me`);
    userId = response.ok ? (await response.json()).id : null;
  }

  if (!userId || !getSessionToken()) return;

  const response = await apiFetch(`${apiBaseUrl}/users/${userId}/fcm-token`, {
    method: 'DELETE',
  });

  if (!response.ok) {
    throw new Error(`Failed to remove FCM token: ${response.status}`);
  }
}

// Called when the student turns notifications off. Rejects if the token could
// not be removed, so the page does not claim they are off.
export async function disablePushNotifications() {
  const registration = currentRegistration;

  forgetFcmRegistration();

  return deleteFcmToken(registration);
}

// Called before logout, while the session is still valid. Never rejects, so a
// failed removal cannot stop the student from logging out.
export async function removeFcmToken() {
  const registration = currentRegistration;
  let timeout;

  forgetFcmRegistration();

  await Promise.race([
    deleteFcmToken(registration).catch((error) => {
      console.warn('[FCM] Could not remove the token on logout:', error);
    }),
    new Promise((resolve) => {
      timeout = setTimeout(resolve, MAX_LOGOUT_WAIT_MS);
    }),
  ]);

  clearTimeout(timeout);
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


import { useCallback, useEffect, useState } from 'react';

import {
  enablePushNotifications,
  forgetFcmRegistration,
  getNotificationPermission,
  registerExistingPermission,
  listenForForegroundMessages,
} from '../../../notifications/pushNotifications';

export default function useStudentNotifications(profileId) {
  // 'granted', 'denied', 'default', or 'unsupported' without the Notification API.
  const [notificationPermission, setNotificationPermission] = useState(
    getNotificationPermission
  );

  const [isEnablingNotifications, setIsEnablingNotifications] =
    useState(false);

  const [isNotificationEnabled, setIsNotificationEnabled] =
    useState(false);

  const [notificationMessage, setNotificationMessage] = useState('');
  const [notificationError, setNotificationError] = useState('');

  // Keep the foreground listener active while the student page is mounted.
  useEffect(() => {
    let unsubscribe;
    let cancelled = false;

    listenForForegroundMessages(({ title, body }) => {
      if (
        typeof window !== 'undefined' &&
        'Notification' in window &&
        Notification.permission === 'granted'
      ) {
        new Notification(title, { body });
      }
    })
      .then((cleanup) => {
        if (cancelled) {
          cleanup?.();
        } else {
          unsubscribe = cleanup;
        }
      })
      .catch((error) => {
        console.error(
          '[FCM] Failed to initialize foreground listener:',
          error
        );
      });

    return () => {
      cancelled = true;
      unsubscribe?.();
    };
  }, []);

  // Permission can change in browser settings while the portal is open.
  // No event fires reliably for this, so re-read it when the student returns.
  useEffect(() => {
    const refreshPermission = () => {
      if (document.visibilityState === 'visible') {
        setNotificationPermission(getNotificationPermission());
      }
    };

    document.addEventListener('visibilitychange', refreshPermission);
    window.addEventListener('focus', refreshPermission);

    return () => {
      document.removeEventListener('visibilitychange', refreshPermission);
      window.removeEventListener('focus', refreshPermission);
    };
  }, []);

  // Register the token only when permission is granted for a known student.
  useEffect(() => {
    setIsNotificationEnabled(false);
    setNotificationError('');

    if (notificationPermission !== 'granted') {
      // A revoked permission invalidates the token, so renew it if re-granted.
      forgetFcmRegistration();
    }

    if (!profileId || notificationPermission !== 'granted') {
      setIsEnablingNotifications(false);
      setNotificationMessage('');
      return undefined;
    }

    let cancelled = false;

    setIsEnablingNotifications(true);

    registerExistingPermission(profileId)
      .then(() => {
        if (!cancelled) {
          setIsNotificationEnabled(true);
          setNotificationMessage(
            (current) =>
              current || 'Notifications are enabled on this browser.'
          );
        }
      })
      .catch((error) => {
        if (!cancelled) {
          console.error(
            '[FCM] Could not refresh notification registration:',
            error
          );

          setNotificationMessage('');
          setNotificationError(
            error.message ||
              'Could not register this browser for notifications.'
          );
        }
      })
      .finally(() => {
        if (!cancelled) {
          setIsEnablingNotifications(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [profileId, notificationPermission]);

  // Called when the student clicks Enable Notifications or Try Again.
  const handleEnableNotifications = useCallback(async () => {
    if (!profileId || isEnablingNotifications) return;

    setIsEnablingNotifications(true);
    setNotificationMessage('');
    setNotificationError('');

    try {
      await enablePushNotifications(profileId);

      setIsNotificationEnabled(true);
      setNotificationMessage('Notifications enabled successfully!');
    } catch (error) {
      console.error('[FCM] Failed to enable notifications:', error);

      setIsNotificationEnabled(false);
      setNotificationError(
        error.message || 'Could not enable notifications.'
      );
    } finally {
      setIsEnablingNotifications(false);
      setNotificationPermission(getNotificationPermission());
    }
  }, [profileId, isEnablingNotifications]);

  return {
    notificationPermission,
    isEnablingNotifications,
    isNotificationEnabled,
    notificationMessage,
    notificationError,
    handleEnableNotifications,
  };
}

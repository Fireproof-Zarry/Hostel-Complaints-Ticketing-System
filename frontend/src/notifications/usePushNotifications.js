
import { useCallback, useEffect, useState } from 'react';

import {
  areNotificationsTurnedOff,
  disablePushNotifications,
  enablePushNotifications,
  forgetFcmRegistration,
  getNotificationPermission,
  registerExistingPermission,
  setNotificationsTurnedOff,
} from './pushNotifications';

export default function usePushNotifications(profileId) {
  // 'granted', 'denied', 'default', or 'unsupported' without the Notification API.
  const [notificationPermission, setNotificationPermission] = useState(
    getNotificationPermission
  );

  const [isEnablingNotifications, setIsEnablingNotifications] =
    useState(false);

  const [isDisablingNotifications, setIsDisablingNotifications] =
    useState(false);

  const [isNotificationEnabled, setIsNotificationEnabled] =
    useState(false);

  const [notificationMessage, setNotificationMessage] = useState('');
  const [notificationError, setNotificationError] = useState('');

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

    if (
      !profileId ||
      notificationPermission !== 'granted' ||
      areNotificationsTurnedOff(profileId)
    ) {
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

  // Called when the student turns notifications on or clicks Try Again.
  const handleEnableNotifications = useCallback(async () => {
    if (!profileId || isEnablingNotifications || isDisablingNotifications) {
      return;
    }

    setIsEnablingNotifications(true);
    setNotificationMessage('');
    setNotificationError('');

    try {
      setNotificationsTurnedOff(profileId, false);

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
  }, [profileId, isEnablingNotifications, isDisablingNotifications]);

  // Called when the student turns notifications off.
  const handleDisableNotifications = useCallback(async () => {
    if (!profileId || isEnablingNotifications || isDisablingNotifications) {
      return;
    }

    setIsDisablingNotifications(true);
    setNotificationError('');

    try {
      await disablePushNotifications();

      setNotificationsTurnedOff(profileId, true);
      setIsNotificationEnabled(false);
      setNotificationMessage('');
    } catch (error) {
      console.error('[FCM] Failed to turn off notifications:', error);

      setNotificationError(
        'Could not turn off notifications. Please try again.'
      );
    } finally {
      setIsDisablingNotifications(false);
    }
  }, [profileId, isEnablingNotifications, isDisablingNotifications]);

  return {
    notificationPermission,
    isEnablingNotifications,
    isDisablingNotifications,
    isNotificationEnabled,
    notificationMessage,
    notificationError,
    handleEnableNotifications,
    handleDisableNotifications,
  };
}

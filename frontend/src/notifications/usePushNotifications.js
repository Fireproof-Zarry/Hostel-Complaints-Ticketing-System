
import { useCallback, useEffect, useRef, useState } from 'react';

import {
  areNotificationsTurnedOff,
  disablePushNotifications,
  enablePushNotifications,
  forgetFcmRegistration,
  getNotificationPermission,
  registerExistingPermission,
  setNotificationsTurnedOff,
} from './pushNotifications';

/** Notification state and the on/off actions for a page. Registers the device when allowed. */
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
  // Bumped when the user turns notifications off, so a registration still in
  // progress cannot switch them back on when it finishes
  const registrationRun = useRef(0);
  const [notificationError, setNotificationError] = useState('');

  // Permission can change in browser settings while the portal is open.
  // No event fires reliably for this, so re-read it when the user returns.
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

  // Register the token only when permission is granted for a known user.
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
    const run = ++registrationRun.current;
    const isCurrent = () => !cancelled && run === registrationRun.current;

    setIsEnablingNotifications(true);

    registerExistingPermission(profileId)
      .then(() => {
        if (isCurrent()) {
          setIsNotificationEnabled(true);
          setNotificationMessage(
            (current) =>
              current || 'Notifications are enabled on this browser.'
          );
        }
      })
      .catch((error) => {
        if (isCurrent()) {
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
        if (isCurrent()) {
          setIsEnablingNotifications(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [profileId, notificationPermission]);

  // Called when the user turns notifications on or clicks Try Again.
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

  // Called when the user turns notifications off. Works while a registration is
  // still in progress too: it shows as off at once, and the token is removed
  // once that registration has finished.
  const handleDisableNotifications = useCallback(async () => {
    if (!profileId || isDisablingNotifications) {
      return;
    }

    registrationRun.current += 1;
    setIsDisablingNotifications(true);
    setIsEnablingNotifications(false);
    setIsNotificationEnabled(false);
    setNotificationError('');

    try {
      await disablePushNotifications();

      setNotificationsTurnedOff(profileId, true);
      setNotificationMessage('');
    } catch (error) {
      console.error('[FCM] Failed to turn off notifications:', error);

      // The token is probably still saved, so they are still on
      setIsNotificationEnabled(true);
      setNotificationError(
        'Could not turn off notifications. Please try again.'
      );
    } finally {
      setIsDisablingNotifications(false);
    }
  }, [profileId, isDisablingNotifications]);

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

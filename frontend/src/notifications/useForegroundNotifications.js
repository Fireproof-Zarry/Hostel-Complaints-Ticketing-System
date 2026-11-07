import { useCallback, useEffect, useState } from 'react';

import { listenForForegroundMessages } from './pushNotifications';

// Every signed-in page uses this, because Firebase shows no popup while a
// HostelFix page is visible and a message nothing listens for is lost.
export default function useForegroundNotifications(onMessage) {
  const [notificationToast, setNotificationToast] = useState(null);

  const dismissNotificationToast = useCallback(() => {
    setNotificationToast(null);
  }, []);

  useEffect(() => {
    let unsubscribe;
    let cancelled = false;

    listenForForegroundMessages(({ title, body }) => {
      setNotificationToast({ title, body });
      onMessage?.();
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
  }, [onMessage]);

  return { notificationToast, dismissNotificationToast };
}

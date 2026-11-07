
importScripts(
  "https://www.gstatic.com/firebasejs/13.2.0/firebase-app-compat.js"
);
importScripts(
  "https://www.gstatic.com/firebasejs/13.2.0/firebase-messaging-compat.js"
);

// vite.config.js fills this in from .env and serves the result as
// /firebase-messaging-sw.js, because files in public/ cannot read env values.
firebase.initializeApp(__FIREBASE_CONFIG__);

// Handle notification clicks. This must be registered before firebase.messaging(),
// whose own click handler stops later handlers and does nothing without a link.
self.addEventListener("notificationclick", (event) => {
  event.stopImmediatePropagation();
  event.notification.close();

  event.waitUntil(
    (async () => {
      // Firebase keeps the message's data under FCM_MSG
      const notificationData = event.notification.data?.FCM_MSG?.data || {};

      // Use the supplied URL if available; otherwise open HostelFix.
      let targetUrl = new URL("/", self.location.origin);
      const hasLink = Boolean(notificationData.url);

      if (hasLink) {
        const requestedUrl = new URL(
          notificationData.url,
          self.location.origin
        );

        // Only allow navigation within HostelFix.
        if (requestedUrl.origin === self.location.origin) {
          targetUrl = requestedUrl;
        }
      }

      const windowClients = await self.clients.matchAll({
        type: "window",
        includeUncontrolled: true,
      });

      // Reuse an existing HostelFix tab if possible. A link needs a tab on that
      // page, which moves itself: this worker does not control tabs, so it
      // cannot navigate them.
      for (const client of windowClients) {
        const clientUrl = new URL(client.url);
        if (clientUrl.origin !== self.location.origin) continue;

        if (!hasLink) return client.focus();

        if (clientUrl.pathname === targetUrl.pathname) {
          client.postMessage({
            type: "OPEN_URL",
            url: targetUrl.pathname + targetUrl.search,
          });
          return client.focus();
        }
      }

      // Otherwise, open a new tab.
      return self.clients.openWindow(targetUrl.href);
    })()
  );
});

// Displays background notifications from the backend's notification payload.
// Do not also call showNotification in onBackgroundMessage, or they appear twice.
firebase.messaging();

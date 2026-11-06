
importScripts(
  "https://www.gstatic.com/firebasejs/13.2.0/firebase-app-compat.js"
);
importScripts(
  "https://www.gstatic.com/firebasejs/13.2.0/firebase-messaging-compat.js"
);

// Firebase configuration must match frontend/src/firebase.js.
firebase.initializeApp({
  apiKey: "AIzaSyCiQ4NNBX_mC30NOrkM9G6aA6JsCQdK21A",
  authDomain: "hostelcomplaints-f6fc6.firebaseapp.com",
  projectId: "hostelcomplaints-f6fc6",
  storageBucket: "hostelcomplaints-f6fc6.firebasestorage.app",
  messagingSenderId: "304613638277",
  appId: "1:304613638277:web:fce6502801c5ea2afde9ec",
});

// Handle notification clicks. This must be registered before firebase.messaging(),
// whose own click handler stops later handlers and does nothing without a link.
self.addEventListener("notificationclick", (event) => {
  event.stopImmediatePropagation();
  event.notification.close();

  event.waitUntil(
    (async () => {
      const notificationData = event.notification.data || {};

      // Use the supplied URL if available; otherwise open HostelFix.
      let targetUrl = new URL("/", self.location.origin);

      if (notificationData.url) {
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

      // Reuse an existing HostelFix tab if possible.
      for (const client of windowClients) {
        if (new URL(client.url).origin === self.location.origin) {
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

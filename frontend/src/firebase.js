
import { initializeApp } from "firebase/app";
import { getMessaging, isSupported } from "firebase/messaging";

const requiredFirebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET,
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID,
  appId: import.meta.env.VITE_FIREBASE_APP_ID,
};

// Validate Firebase configuration before initialization.
const missingConfig = Object.entries(requiredFirebaseConfig)
  .filter(([, value]) => !value)
  .map(([key]) => key);

if (missingConfig.length > 0) {
  throw new Error(
    `Missing Firebase environment variables: ${missingConfig.join(", ")}`
  );
}

const app = initializeApp(requiredFirebaseConfig);

// Initialize Firebase Messaging only when the browser supports it.
export const messagingPromise = isSupported().then((supported) => {
  return supported ? getMessaging(app) : null;
});

export default app;

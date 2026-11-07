// Firebase keeps a single message handler, and unsubscribing clears whatever is
// set at that moment. StrictMode mounts pages twice, so the first mount's cleanup
// would remove the second mount's handler and messages would be lost silently.
// So subscribe once, for good, and let pages add and remove their own listeners.
export function createForegroundListeners(subscribe) {
  const listeners = new Set();
  let isSubscribed = false;

  return function addListener(listener) {
    listeners.add(listener);
    if (!isSubscribed) {
      isSubscribed = true;
      subscribe((payload) => listeners.forEach((current) => current(payload)));
    }
    return () => listeners.delete(listener);
  };
}

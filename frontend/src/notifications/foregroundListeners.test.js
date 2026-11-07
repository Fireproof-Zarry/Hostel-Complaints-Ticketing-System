import assert from 'node:assert/strict';
import test from 'node:test';
import { createForegroundListeners } from './foregroundListeners.js';

// Behaves like Firebase's onMessage: one slot, and unsubscribing empties it
function fakeFirebase() {
  const messaging = { handler: null };
  const subscribe = (handler) => {
    messaging.handler = handler;
    return () => {
      messaging.handler = null;
    };
  };
  return { messaging, subscribe };
}

test('a page mounted twice by StrictMode still receives messages', () => {
  const { messaging, subscribe } = fakeFirebase();
  const addListener = createForegroundListeners(subscribe);
  const received = [];

  const removeFirstMount = addListener(() => received.push('first'));
  addListener(() => received.push('second'));
  removeFirstMount();

  messaging.handler({ notification: { title: 'Hello' } });

  assert.deepEqual(received, ['second']);
});

test('subscribes to Firebase only once', () => {
  let subscriptions = 0;
  const addListener = createForegroundListeners(() => {
    subscriptions += 1;
  });

  addListener(() => {});
  addListener(() => {});

  assert.equal(subscriptions, 1);
});

test('a page that has left stops receiving messages', () => {
  const { messaging, subscribe } = fakeFirebase();
  const addListener = createForegroundListeners(subscribe);
  const received = [];

  const remove = addListener(() => received.push('page'));
  remove();
  messaging.handler({});

  assert.deepEqual(received, []);
});

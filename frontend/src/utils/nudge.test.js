import assert from 'node:assert/strict';
import test from 'node:test';
import { isAwaitingNudgeResponse, nudgeDaysLeft, nudgeTooltip } from './nudge.js';

// Built from local times and sent the way the server sends them (UTC, no
// offset), so the tests pass in any time zone
const local = (day, hour, minute = 0) => new Date(2026, 10, day, hour, minute);
const serverTime = (date) => date.toISOString().slice(0, -1);

const NOW = local(7, 9).getTime();
const LAST = '2026-11-07T10:00:00';

test('days left counts calendar days, not 24-hour periods', () => {
  // Filed on the 6th in the afternoon: active on the 13th, 6 days from the 7th
  assert.equal(nudgeDaysLeft(serverTime(local(13, 18, 15)), NOW), 6);
  assert.equal(nudgeDaysLeft(serverTime(local(13, 0, 30)), local(7, 23, 30).getTime()), 6);
});

test('tomorrow is 1 day and later today is 0', () => {
  assert.equal(nudgeDaysLeft(serverTime(local(8, 8)), NOW), 1);
  assert.equal(nudgeDaysLeft(serverTime(local(7, 18)), NOW), 0);
});

test('days left never goes below 0 if the wait ended while the page was open', () => {
  assert.equal(nudgeDaysLeft(serverTime(local(1, 9)), NOW), 0);
});

test('an active button has no tooltip', () => {
  assert.equal(nudgeTooltip({ canNudge: true, nextNudgeAt: serverTime(local(1, 9)) }, NOW), null);
});

test('a greyed-out button says how many days are left', () => {
  assert.equal(
    nudgeTooltip({ canNudge: false, nextNudgeAt: serverTime(local(13, 18, 15)) }, NOW),
    'Will be active 6 days later',
  );
});

test('one day left is singular', () => {
  assert.equal(
    nudgeTooltip({ canNudge: false, nextNudgeAt: serverTime(local(8, 8)) }, NOW),
    'Will be active 1 day later',
  );
});

test('a button that turns on later today says so, without a time', () => {
  assert.equal(
    nudgeTooltip({ canNudge: false, nextNudgeAt: serverTime(local(7, 18)) }, NOW),
    'Will be active today',
  );
});

test('once the wait is over, a page that has not reloaded says to reload', () => {
  assert.equal(
    nudgeTooltip({ canNudge: false, nextNudgeAt: serverTime(local(7, 8)) }, NOW),
    'Please reload to make it active',
  );
  assert.equal(
    nudgeTooltip({ canNudge: false, nextNudgeAt: serverTime(local(1, 9)) }, NOW),
    'Please reload to make it active',
  );
});

test('a closed complaint has no date, so the tooltip says why', () => {
  assert.equal(
    nudgeTooltip({ canNudge: false, nextNudgeAt: null }, NOW),
    'Only unresolved complaints can be nudged.',
  );
});

test('the admin badge shows on any unresolved nudged complaint', () => {
  for (const status of ['PENDING', 'ASSIGNED', 'IN_PROGRESS']) {
    assert.equal(isAwaitingNudgeResponse({ status, lastNudgedAt: LAST }), true);
  }
});

test('the admin badge goes away once a nudged complaint is closed', () => {
  for (const status of ['RESOLVED', 'REJECTED']) {
    assert.equal(isAwaitingNudgeResponse({ status, lastNudgedAt: LAST }), false);
  }
});

test('a complaint never nudged gets no badge', () => {
  assert.equal(isAwaitingNudgeResponse({ status: 'PENDING', lastNudgedAt: null }), false);
});

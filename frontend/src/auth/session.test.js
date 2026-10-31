import assert from 'node:assert/strict';
import test from 'node:test';
import { decodeTokenPayload, getTokenExpiry, isTokenExpired, sessionEndReason } from './session.js';

function token(payload) {
  const encode = (value) => Buffer.from(JSON.stringify(value)).toString('base64url');
  return `${encode({ alg: 'RS256' })}.${encode(payload)}.signature`;
}

test('reads the payload and expiry of a JWT', () => {
  const jwt = token({ email: 'student@smail.iitm.ac.in', exp: 1_800_000_000 });
  assert.equal(decodeTokenPayload(jwt).email, 'student@smail.iitm.ac.in');
  assert.equal(getTokenExpiry(jwt), 1_800_000_000_000);
});

test('unreadable tokens have no payload or expiry', () => {
  for (const bad of [null, undefined, '', 'not-a-jwt', 'a.%%%.c', `a.${Buffer.from('nope').toString('base64url')}.c`]) {
    assert.equal(decodeTokenPayload(bad), null, String(bad));
    assert.equal(getTokenExpiry(bad), null, String(bad));
  }
});

test('treats tokens as expired a minute early, and unknown tokens as expired', () => {
  const now = 1_800_000_000_000;
  assert.equal(isTokenExpired(token({ exp: now / 1000 + 3600 }), now), false);
  assert.equal(isTokenExpired(token({ exp: now / 1000 + 30 }), now), true);
  assert.equal(isTokenExpired(token({ exp: now / 1000 - 10 }), now), true);
  assert.equal(isTokenExpired(token({}), now), true);
  assert.equal(isTokenExpired(null, now), true);
});

test('maps the backend WWW-Authenticate header to a reason', () => {
  assert.equal(sessionEndReason('Bearer error="invalid_token", error_description="Jwt expired at 2026-11-01T10:00:00Z"'), 'expired');
  assert.equal(sessionEndReason('Bearer error="invalid_token", error_description="Jwt used before 2026-11-01T10:00:00Z"'), 'clock');
  assert.equal(sessionEndReason('Bearer error="invalid_token", error_description="The aud claim is not valid"'), 'rejected');
  assert.equal(sessionEndReason(null), 'rejected');
});

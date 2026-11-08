import assert from 'node:assert/strict';
import test from 'node:test';
import { decodeTokenPayload, getTokenExpiry, isStaleToken, isTokenExpired, isTokenRejection, sessionEndReason } from './session.js';

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
  assert.equal(sessionEndReason('Bearer error="invalid_token", error_description="This portal is for Bhadra residents. If you live in Bhadra, contact the hostel office."'), 'notResident');
  assert.equal(sessionEndReason(null), 'rejected');
});

test('a failure is stale only when a different token is stored now', () => {
  assert.equal(isStaleToken('old-token', 'new-token'), true);
  assert.equal(isStaleToken('same-token', 'same-token'), false);
  // Nothing stored anymore: the failure is real, not stale
  assert.equal(isStaleToken('old-token', null), false);
  assert.equal(isStaleToken('old-token', ''), false);
});

test('only an invalid_token 401 counts as a rejected sign-in', () => {
  assert.equal(isTokenRejection('Bearer error="invalid_token", error_description="Jwt expired at 2026-11-01T10:00:00Z"'), true);
  // A bare challenge: the server could not authenticate the request (e.g. a crash during the login check)
  assert.equal(isTokenRejection('Bearer resource_metadata="http://localhost:8080/.well-known/oauth-protected-resource"'), false);
  assert.equal(isTokenRejection(null), false);
});

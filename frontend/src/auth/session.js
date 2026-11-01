// Session handling for the Google ID token stored after sign-in.
// Google ID tokens expire after 1 hour and are not refreshed, so the app
// checks the expiry itself and sends the user back to sign in cleanly.

export const TOKEN_KEY = 'idToken';
const NOTICE_KEY = 'hostelfix.sessionNotice';
// Treat the token as expired slightly early to absorb small clock differences
const EXPIRY_MARGIN_MS = 60 * 1000;

export const SESSION_MESSAGES = {
  expired: 'Your session expired. Please sign in again.',
  clock: 'Your sign-in could not be verified because of a clock mismatch between your device and the server. Please try again in a minute.',
  rejected: 'Your sign-in could not be verified. Please sign in again.',
};

// ---- Pure helpers (unit-tested) ----

/** Decoded payload of a JWT, or null if it is not a readable JWT. No signature check. */
export function decodeTokenPayload(token) {
  const part = typeof token === 'string' ? token.split('.')[1] : null;
  if (!part) return null;
  try {
    const base64 = part.replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4);
    return JSON.parse(atob(padded));
  } catch {
    return null;
  }
}

/** Expiry time in milliseconds since the epoch, or null if unknown. */
export function getTokenExpiry(token) {
  const exp = decodeTokenPayload(token)?.exp;
  return typeof exp === 'number' ? exp * 1000 : null;
}

/** True when there is no usable token or it expires within the safety margin. */
export function isTokenExpired(token, now = Date.now()) {
  const expiry = getTokenExpiry(token);
  return expiry === null || expiry - EXPIRY_MARGIN_MS <= now;
}

/**
 * True when a request failed with a token that is no longer the stored one,
 * e.g. the user signed in again (here or in another tab) while it was in flight.
 * Such a failure must not end the newer session.
 */
export function isStaleToken(failedToken, currentToken) {
  return Boolean(currentToken) && currentToken !== failedToken;
}

/**
 * True when a 401's WWW-Authenticate header says the token itself was rejected
 * (error="invalid_token"). A bare "Bearer ..." header means the server failed
 * to authenticate the request for another reason, not that the token is bad.
 */
export function isTokenRejection(wwwAuthenticate) {
  return /error="invalid_token"/i.test(wwwAuthenticate ?? '');
}

/**
 * Why the backend rejected the token, from its WWW-Authenticate header,
 * e.g. 'Bearer error="invalid_token", error_description="Jwt expired at ..."'.
 */
export function sessionEndReason(wwwAuthenticate) {
  const header = wwwAuthenticate ?? '';
  if (/expired/i.test(header)) return 'expired';
  if (/used before|not yet valid/i.test(header)) return 'clock';
  return 'rejected';
}

// ---- Browser session state ----

const listeners = new Set();

export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function saveToken(token) {
  localStorage.setItem(TOKEN_KEY, token);
  sessionStorage.removeItem(NOTICE_KEY);
}

/** Drops a token that failed to sign in. */
export function clearToken() {
  localStorage.removeItem(TOKEN_KEY);
}

/** Registers a callback run when the session ends; returns an unsubscribe function. */
export function onSessionEnded(listener) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

/**
 * Ends the session automatically (expired or rejected token); the login page
 * explains why. When failedToken is given and a different token is stored by
 * now, the failure is stale and the current session is kept (returns false).
 */
export function endSession(reason = 'expired', failedToken) {
  if (failedToken !== undefined && isStaleToken(failedToken, getToken())) {
    return false;
  }
  // Visible in the browser console (F12) to diagnose unexpected sign-outs
  console.warn(`[HostelFix] Session ended: ${reason}`);
  localStorage.removeItem(TOKEN_KEY);
  sessionStorage.setItem(NOTICE_KEY, reason);
  listeners.forEach((listener) => listener(reason));
  return true;
}

/** Explicit "Log out": also stops Google from signing the user straight back in. */
export function logout() {
  localStorage.removeItem(TOKEN_KEY);
  sessionStorage.removeItem(NOTICE_KEY);
  window.google?.accounts?.id?.disableAutoSelect();
}

/**
 * Reason the last session ended automatically, or null after an explicit
 * logout. Read-only (safe during render); cleared by saveToken and logout.
 */
export function getSessionNotice() {
  return sessionStorage.getItem(NOTICE_KEY);
}

export class SessionEndedError extends Error {
  constructor(reason) {
    super(SESSION_MESSAGES[reason] ?? SESSION_MESSAGES.rejected);
    this.name = 'SessionEndedError';
    this.reason = reason;
  }
}

/**
 * fetch() for authenticated API calls. Adds the current token, and ends the
 * session (instead of returning the response) when the token is expired or
 * the backend rejects it with 401.
 */
export async function apiFetch(url, options = {}) {
  return sendWithToken(url, options, false);
}

async function sendWithToken(url, options, isRetry) {
  const token = getToken();
  if (!token || isTokenExpired(token)) {
    endSession('expired', token ?? undefined);
    throw new SessionEndedError('expired');
  }

  const headers = new Headers(options.headers);
  headers.set('Authorization', `Bearer ${token}`);
  const response = await fetch(url, { ...options, headers });

  if (response.status === 401) {
    // Signed in again while this request was in flight: retry once with the new token
    if (!isRetry && isStaleToken(token, getToken())) {
      return sendWithToken(url, options, true);
    }
    const wwwAuthenticate = response.headers.get('WWW-Authenticate');
    // Full detail for diagnosing unexpected sign-outs (never the token itself)
    console.warn(`[HostelFix] 401 from ${options.method ?? 'GET'} ${url}: `
      + (wwwAuthenticate ?? 'WWW-Authenticate unreadable (backend not exposing it via CORS?)'));
    // Only a genuine token rejection ends the session; anything else is a
    // server-side problem and is reported like any other failed request
    if (wwwAuthenticate !== null && !isTokenRejection(wwwAuthenticate)) {
      return response;
    }
    const reason = sessionEndReason(wwwAuthenticate);
    endSession(reason, token);
    throw new SessionEndedError(reason);
  }
  return response;
}

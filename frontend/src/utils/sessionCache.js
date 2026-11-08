import { decodeTokenPayload, getToken } from '../auth/session'

// Data shown straight away after a reload or on returning to a page, while fresh data
// loads. Kept in this browser tab's session storage (cleared on logout) and tied to the
// signed-in account, so another account never sees it. logout() in auth/session.js
// removes every key used here.

function currentAccount() {
  const token = getToken()
  return token ? decodeTokenPayload(token)?.email ?? null : null
}

/** The value stored under key for the signed-in account, or null. */
export function readSessionCache(key) {
  const account = currentAccount()
  if (!account) return null
  try {
    const cached = JSON.parse(sessionStorage.getItem(key))
    return cached?.account === account ? cached.value : null
  } catch {
    return null
  }
}

/** Stores value under key for the signed-in account. */
export function writeSessionCache(key, value) {
  const account = currentAccount()
  if (!account) return
  try {
    sessionStorage.setItem(key, JSON.stringify({ account, value }))
  } catch {
    // Without storage the next reload simply shows Loading.
  }
}

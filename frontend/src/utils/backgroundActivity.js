import { useSyncExternalStore } from 'react'

// Requests refreshing data that is already on screen. While any are running,
// BackgroundActivityIndicator shows a small "Updating…" in the corner instead of
// the page swapping its content for a Loading message, then "Updated!" if they all worked.
let running = 0
let batchFailed = false
// Replaced on every change, as useSyncExternalStore needs; batch counts finished batches
let snapshot = { busy: false, batch: 0, lastBatchOk: true }
const listeners = new Set()

function subscribe(listener) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

function setSnapshot(next) {
  snapshot = next
  listeners.forEach((listener) => listener())
}

// A request failed if it threw, or resolved to false or an unsuccessful fetch Response
function isFailure(value) {
  return value === false || (typeof Response !== 'undefined' && value instanceof Response && !value.ok)
}

function finish(failed) {
  running -= 1
  if (failed) batchFailed = true
  if (running > 0) return
  setSnapshot({ busy: false, batch: snapshot.batch + 1, lastBatchOk: !batchFailed })
  batchFailed = false
}

/** Counts promise as background work until it settles; returns it unchanged. */
export function trackBackground(promise) {
  running += 1
  if (running === 1) setSnapshot({ ...snapshot, busy: true })
  let settled = false
  const settle = (failed) => {
    if (settled) return
    settled = true
    finish(failed)
  }
  promise.then((value) => settle(isFailure(value)), () => settle(true))
  return promise
}

/**
 * { busy, batch, lastBatchOk }: whether background work is running, how many batches
 * (runs from first start to last finish) have ended, and whether the last one all worked.
 */
export function useBackgroundActivity() {
  return useSyncExternalStore(subscribe, () => snapshot)
}

// For tests
export function getBackgroundActivity() {
  return snapshot
}

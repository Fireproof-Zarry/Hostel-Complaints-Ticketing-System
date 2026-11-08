import { useEffect, useRef, useState } from 'react'
import { CheckCircle2, RefreshCw } from 'lucide-react'

import { useBackgroundActivity } from '../utils/backgroundActivity'

// Quick refreshes finish before this and show nothing, so the pill does not flicker
const SHOW_AFTER_MS = 400
const UPDATED_FOR_MS = 1500

/**
 * A small pill in the bottom-right corner: "Updating…" while data refreshes in the
 * background, then "Updated!" for a moment if it worked. A failed refresh just hides
 * it, since the page shows its own error.
 */
export default function BackgroundActivityIndicator() {
  const { busy, batch, lastBatchOk } = useBackgroundActivity()
  // 'hidden' | 'updating' | 'updated'
  const [phase, setPhase] = useState('hidden')
  const shownUpdatingRef = useRef(false)

  // Show "Updating…" once the work has run for a moment
  useEffect(() => {
    if (!busy) return undefined
    const timer = setTimeout(() => {
      shownUpdatingRef.current = true
      setPhase('updating')
    }, SHOW_AFTER_MS)
    return () => clearTimeout(timer)
  }, [busy])

  // A batch ended: confirm it if "Updating…" was showing
  useEffect(() => {
    if (batch === 0) return undefined
    const wasShowing = shownUpdatingRef.current
    shownUpdatingRef.current = false
    if (!wasShowing || !lastBatchOk) {
      setPhase('hidden')
      return undefined
    }
    setPhase('updated')
    const timer = setTimeout(() => setPhase('hidden'), UPDATED_FOR_MS)
    return () => clearTimeout(timer)
  }, [batch, lastBatchOk])

  const updating = busy && phase === 'updating'
  const updated = !busy && phase === 'updated'

  return (
    <div role="status" aria-live="polite" className="pointer-events-none fixed bottom-4 right-4 z-50">
      {(updating || updated) && (
        <span className="flex items-center gap-2 rounded-full border border-slate-200 bg-white px-3 py-1.5 text-xs font-medium text-slate-600 shadow-md">
          {updating ? (
            <RefreshCw className="h-3.5 w-3.5 animate-spin text-indigo-600" aria-hidden="true" />
          ) : (
            <CheckCircle2 className="h-3.5 w-3.5 text-emerald-600" aria-hidden="true" />
          )}
          {updating ? 'Updating…' : 'Updated!'}
        </span>
      )}
    </div>
  )
}

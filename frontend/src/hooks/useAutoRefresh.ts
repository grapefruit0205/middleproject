import { useEffect, useRef } from 'react'

type AutoRefreshOptions = {
  enabled: boolean
  busy: boolean
  onRefresh: (signal: AbortSignal) => Promise<void>
  intervalMs?: number
}

/** Poll only while the dashboard can be used; the caller owns initial loading and errors. */
export default function useAutoRefresh({ enabled, busy, onRefresh, intervalMs = 30_000 }: AutoRefreshOptions): void {
  const current = useRef({ enabled, busy, onRefresh, intervalMs })
  const synchronize = useRef<(() => void) | null>(null)
  current.current = { enabled, busy, onRefresh, intervalMs }

  useEffect(() => {
    let disposed = false
    let timer: number | undefined
    let inFlight: AbortController | null = null
    let resumeAfterAbort = false
    let lastResumeAt: number | null = null

    const canRefresh = () => !disposed && current.current.enabled && !current.current.busy
      && document.visibilityState === 'visible' && navigator.onLine

    const clearTimer = () => {
      window.clearTimeout(timer)
      timer = undefined
    }

    const suspend = () => {
      clearTimer()
      resumeAfterAbort = false
      inFlight?.abort()
    }

    const schedule = () => {
      clearTimer()
      if (!canRefresh() || inFlight) return
      const delay = current.current.intervalMs
      timer = window.setTimeout(() => { void refresh() }, Number.isFinite(delay) && delay > 0 ? delay : 30_000)
    }

    const refresh = async () => {
      if (!canRefresh() || inFlight) return
      clearTimer()
      const controller = new AbortController()
      inFlight = controller
      try {
        await current.current.onRefresh(controller.signal)
      } catch {
        // The caller displays failures and may disable refreshing. Never leak a rejected timer promise.
      } finally {
        inFlight = null
        if (disposed) return
        if (resumeAfterAbort && canRefresh()) {
          resumeAfterAbort = false
          lastResumeAt = Date.now()
          void refresh()
        } else {
          schedule()
        }
      }
    }

    const resume = () => {
      if (!canRefresh()) {
        suspend()
        return
      }
      if (inFlight) {
        // Wait for cancellation to settle before replacing an interrupted request.
        resumeAfterAbort = inFlight.signal.aborted
        return
      }
      const now = Date.now()
      if (lastResumeAt !== null && now - lastResumeAt < 1_000) return
      lastResumeAt = now
      void refresh()
    }

    const sync = () => {
      resumeAfterAbort = false
      if (canRefresh()) schedule()
      else suspend()
    }

    synchronize.current = sync
    document.addEventListener('visibilitychange', resume)
    window.addEventListener('focus', resume)
    window.addEventListener('online', resume)
    window.addEventListener('offline', suspend)
    schedule()

    return () => {
      disposed = true
      suspend()
      synchronize.current = null
      document.removeEventListener('visibilitychange', resume)
      window.removeEventListener('focus', resume)
      window.removeEventListener('online', resume)
      window.removeEventListener('offline', suspend)
    }
  }, [])

  useEffect(() => {
    synchronize.current?.()
  }, [enabled, busy, intervalMs])
}

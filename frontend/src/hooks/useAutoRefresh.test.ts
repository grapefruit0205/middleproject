import { act, cleanup, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import useAutoRefresh from './useAutoRefresh'

let visibility: DocumentVisibilityState
let online: boolean

async function advance(ms: number) {
  await act(async () => { await vi.advanceTimersByTimeAsync(ms) })
}

async function visibilityChange(value: DocumentVisibilityState) {
  visibility = value
  await act(async () => { document.dispatchEvent(new Event('visibilitychange')) })
}

beforeEach(() => {
  vi.useFakeTimers()
  visibility = 'visible'
  online = true
  vi.spyOn(document, 'visibilityState', 'get').mockImplementation(() => visibility)
  vi.spyOn(navigator, 'onLine', 'get').mockImplementation(() => online)
})

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.useRealTimers()
})

describe('useAutoRefresh', () => {
  it('waits for the interval, pauses in hidden tabs, and refreshes when returning', async () => {
    const onRefresh = vi.fn().mockResolvedValue(undefined)
    renderHook(() => useAutoRefresh({ enabled: true, busy: false, onRefresh }))

    expect(onRefresh).not.toHaveBeenCalled()
    await advance(29_999)
    expect(onRefresh).not.toHaveBeenCalled()
    await advance(1)
    expect(onRefresh).toHaveBeenCalledTimes(1)

    await visibilityChange('hidden')
    await advance(90_000)
    expect(onRefresh).toHaveBeenCalledTimes(1)
    await visibilityChange('visible')
    expect(onRefresh).toHaveBeenCalledTimes(2)
    await act(async () => { window.dispatchEvent(new Event('focus')) })
    expect(onRefresh).toHaveBeenCalledTimes(2)
    await advance(30_000)
    expect(onRefresh).toHaveBeenCalledTimes(3)
  })

  it('never overlaps requests and measures the next interval from completion', async () => {
    let resolve!: () => void
    const onRefresh = vi.fn().mockImplementation(() => new Promise<void>(done => { resolve = done }))
    renderHook(() => useAutoRefresh({ enabled: true, busy: false, onRefresh }))

    await advance(30_000)
    await act(async () => {
      window.dispatchEvent(new Event('focus'))
      document.dispatchEvent(new Event('visibilitychange'))
    })
    await advance(90_000)
    expect(onRefresh).toHaveBeenCalledTimes(1)
    await act(async () => { resolve() })
    await advance(29_999)
    expect(onRefresh).toHaveBeenCalledTimes(1)
    await advance(1)
    expect(onRefresh).toHaveBeenCalledTimes(2)
  })

  it('uses the newest callback without resetting a pending interval', async () => {
    const first = vi.fn().mockResolvedValue(undefined)
    const second = vi.fn().mockResolvedValue(undefined)
    const { rerender } = renderHook(({ onRefresh }) => useAutoRefresh({ enabled: true, busy: false, onRefresh }), {
      initialProps: { onRefresh: first },
    })
    await advance(20_000)
    rerender({ onRefresh: second })
    await advance(10_000)
    expect(first).not.toHaveBeenCalled()
    expect(second).toHaveBeenCalledTimes(1)
  })

  it('aborts for mutations and disabling, then resumes from a full interval', async () => {
    let signal!: AbortSignal
    const onRefresh = vi.fn().mockImplementation((nextSignal: AbortSignal) => {
      signal = nextSignal
      return new Promise<void>((_, reject) => signal.addEventListener('abort', () => reject(new Error('aborted')), { once: true }))
    })
    const { rerender } = renderHook(({ enabled, busy }) => useAutoRefresh({ enabled, busy, onRefresh }), {
      initialProps: { enabled: true, busy: false },
    })
    await advance(30_000)
    rerender({ enabled: true, busy: true })
    expect(signal.aborted).toBe(true)
    await advance(60_000)
    expect(onRefresh).toHaveBeenCalledTimes(1)
    rerender({ enabled: true, busy: false })
    await advance(29_999)
    expect(onRefresh).toHaveBeenCalledTimes(1)
    await advance(1)
    expect(onRefresh).toHaveBeenCalledTimes(2)
    rerender({ enabled: false, busy: false })
    expect(signal.aborted).toBe(true)
    await advance(60_000)
    expect(onRefresh).toHaveBeenCalledTimes(2)
  })

  it('cancels hidden and offline work and refreshes immediately once online', async () => {
    const signals: AbortSignal[] = []
    const onRefresh = vi.fn().mockImplementation((signal: AbortSignal) => {
      signals.push(signal)
      return new Promise<void>((_, reject) => signal.addEventListener('abort', () => reject(new Error('aborted')), { once: true }))
    })
    renderHook(() => useAutoRefresh({ enabled: true, busy: false, onRefresh }))
    await advance(30_000)
    await visibilityChange('hidden')
    expect(signals[0].aborted).toBe(true)
    await visibilityChange('visible')
    expect(onRefresh).toHaveBeenCalledTimes(2)
    online = false
    await act(async () => { window.dispatchEvent(new Event('offline')) })
    expect(signals[1].aborted).toBe(true)
    await advance(60_000)
    expect(onRefresh).toHaveBeenCalledTimes(2)
    online = true
    await act(async () => { window.dispatchEvent(new Event('online')) })
    expect(onRefresh).toHaveBeenCalledTimes(3)
  })

  it('waits for an aborted request to settle before a rapid tab return starts another', async () => {
    let resolve!: () => void
    const onRefresh = vi.fn().mockImplementation(() => new Promise<void>(done => { resolve = done }))
    const { unmount } = renderHook(() => useAutoRefresh({ enabled: true, busy: false, onRefresh }))
    await advance(30_000)
    const signal = onRefresh.mock.calls[0][0] as AbortSignal
    await visibilityChange('hidden')
    await visibilityChange('visible')
    expect(signal.aborted).toBe(true)
    expect(onRefresh).toHaveBeenCalledTimes(1)
    await act(async () => { resolve() })
    expect(onRefresh).toHaveBeenCalledTimes(2)
    unmount()
    expect((onRefresh.mock.calls[1][0] as AbortSignal).aborted).toBe(true)
    await act(async () => { resolve() })
    await advance(90_000)
    await act(async () => { window.dispatchEvent(new Event('focus')) })
    expect(onRefresh).toHaveBeenCalledTimes(2)
    expect(vi.getTimerCount()).toBe(0)
  })
})

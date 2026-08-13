import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import App from './App'

function mockFetchOnce(value: unknown): void {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(value))
}

beforeEach(() => {
  vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('fetch not stubbed for this test')))
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('App', () => {
  it('renders the project title and the initial Checking backend state', async () => {
    render(<App />)
    expect(screen.getByRole('heading', { name: 'Reminder Platform' })).toBeInTheDocument()
    expect(screen.getByText('Checking backend')).toBeInTheDocument()
    expect(await screen.findByText('Backend unavailable')).toBeInTheDocument()
  })

  it('shows Backend ready when readiness returns HTTP 200 with status UP', async () => {
    mockFetchOnce({ ok: true, json: async () => ({ status: 'UP' }) })
    render(<App />)
    expect(await screen.findByText('Backend ready')).toBeInTheDocument()
  })

  it('shows Backend unavailable when readiness rejects', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('network down')))
    render(<App />)
    expect(await screen.findByText('Backend unavailable')).toBeInTheDocument()
  })

  it('shows Backend unavailable when readiness returns a non-200 response', async () => {
    mockFetchOnce({ ok: false, status: 503 })
    render(<App />)
    expect(await screen.findByText('Backend unavailable')).toBeInTheDocument()
  })
})

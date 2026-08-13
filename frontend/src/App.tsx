import { useEffect, useState } from 'react'

type BackendStatus = 'checking' | 'ready' | 'unavailable'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api'

function App() {
  const [status, setStatus] = useState<BackendStatus>('checking')

  useEffect(() => {
    let cancelled = false

    fetch(`${API_BASE_URL}/actuator/health/readiness`)
      .then((response) => {
        if (!response.ok) {
          setStatus('unavailable')
          return null
        }
        return response.json()
      })
      .then((body) => {
        if (cancelled) {
          return
        }
        if (body && body.status === 'UP') {
          setStatus('ready')
        } else {
          setStatus('unavailable')
        }
      })
      .catch(() => {
        if (!cancelled) {
          setStatus('unavailable')
        }
      })

    return () => {
      cancelled = true
    }
  }, [])

  const statusText =
    status === 'checking' ? 'Checking backend' : status === 'ready' ? 'Backend ready' : 'Backend unavailable'

  return (
    <main className="shell">
      <h1>Reminder Platform</h1>
      <p className="status" data-testid="backend-status">
        {statusText}
      </p>
    </main>
  )
}

export default App

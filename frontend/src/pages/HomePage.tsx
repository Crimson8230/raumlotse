import { useEffect, useState } from 'react'
import { getHealth } from '../API/health'
import { useAuth } from '../auth/useAuth'
import { MyUpcomingReservations } from '../components/MyUpcomingReservations/MyUpcomingReservations'
import './HomePage.css'

function HomePage() {
  const [backendStatus, setBackendStatus] = useState<string>('checking...')
  const auth = useAuth()

  useEffect(() => {
    getHealth()
      .then((data) => {
        setBackendStatus(data.status)
      })
      .catch(() => {
        setBackendStatus('unreachable')
      })
  }, [])

  const statusClassName =
    backendStatus === 'checking...' ? 'status-loading' : backendStatus === 'unreachable' ? 'status-unreachable' : undefined

  return (
    <main>
      <h1>Raumlotse</h1>
      <p>Seminar- und Unterrichtsraumreservierung</p>
      <p>
        Backend-Status: <span className={statusClassName}>{backendStatus}</span>
      </p>

      {auth.state === 'authenticated' && <MyUpcomingReservations />}
    </main>
  )
}

export default HomePage

import { useEffect, useState } from 'react'
import { getHealth } from '../API/health'
import { useAuth } from '../auth/useAuth'
import { useCurrentRoles } from '../auth/useCurrentRoles'
import { MyUpcomingReservations } from '../components/MyUpcomingReservations/MyUpcomingReservations'
import './HomePage.css'

function HomePage() {
  const [backendStatus, setBackendStatus] = useState<string>('checking...')
  const auth = useAuth()
  const current = useCurrentRoles(auth.state === 'authenticated')

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

      {auth.state === 'authenticated' && !current.loading && !current.failed && current.permissions.length === 0 &&
        <p role="status">Für dieses Konto sind derzeit keine Funktionen freigegeben.</p>}
      {auth.state === 'authenticated' && current.permissions.includes('READ') && <MyUpcomingReservations />}
    </main>
  )
}

export default HomePage

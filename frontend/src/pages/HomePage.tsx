import { useEffect, useState } from 'react'
import { getHealth } from '../API/health'
import './HomePage.css'

function HomePage() {
  const [backendStatus, setBackendStatus] = useState<string>('checking...')

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
        Backend status: <span className={statusClassName}>{backendStatus}</span>
      </p>
    </main>
  )
}

export default HomePage

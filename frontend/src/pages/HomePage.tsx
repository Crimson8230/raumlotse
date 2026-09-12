import { useEffect, useState } from 'react'
import { getHealth } from '../API/health'

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

  return (
    <main>
      <h1>Raumlotse</h1>
      <p>Seminar- und Unterrichtsraumreservierung</p>
      <p>Backend status: {backendStatus}</p>
    </main>
  )
}

export default HomePage
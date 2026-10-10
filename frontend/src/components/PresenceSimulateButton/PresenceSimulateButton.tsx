import { useState } from 'react'
import { formatApiError } from '../../API/client'
import { simulatePresence, type PresenceEventResult } from '../../API/presence'
import { useAdminMode } from '../../auth/useAdminMode'

function clock(iso: string): string {
  return new Date(iso).toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

/**
 * Stand-in for the room's motion sensor (feature 014), shown only in administration mode. A motion event is recorded
 * for the booking in use and never checks a booking in or switches a device.
 */
export function PresenceSimulateButton({ roomId, lastPresenceAt }: { roomId: string; lastPresenceAt: string | null }) {
  const { adminMode } = useAdminMode()
  const [result, setResult] = useState<PresenceEventResult | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (!adminMode) return null

  async function simulate() {
    setBusy(true)
    setError(null)
    try {
      setResult(await simulatePresence(roomId))
    } catch (err) {
      setError(formatApiError(err))
    } finally {
      setBusy(false)
    }
  }

  const shownPresence = result?.lastPresenceAt ?? lastPresenceAt
  return (
    <div className="presence-simulation">
      <button type="button" onClick={() => void simulate()} disabled={busy}>Bewegung simulieren</button>
      {result && !result.recorded ? (
        <p role="status">Keine aktive Buchung – Bewegung nicht erfasst</p>
      ) : (
        shownPresence && <p role="status">Zuletzt Bewegung erkannt: {clock(shownPresence)}</p>
      )}
      {error && <p role="alert">{error}</p>}
    </div>
  )
}

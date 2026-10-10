import { useEffect, useState } from 'react'
import { useParams, useSearchParams } from 'react-router-dom'
import { checkIn, getCheckInPreview } from '../API/checkIn'
import { formatApiError } from '../API/client'
import { RoomDeviceIcons } from '../components/RoomDeviceIcons/RoomDeviceIcons'
import type { CheckInMethod, CheckInPreview, CheckInResult } from '../types/checkIn'
import { formatTime } from '../utils/date'

const DEVICE_NAMES = { LIGHTING: 'Licht', VENTILATION: 'Lüftung', DOOR: 'Tür' } as const

/**
 * On-site check-in (feature 014), opened from the room's QR code (`?method=qr`) or NFC tag (`?method=nfc`).
 * Nothing is confirmed on page load; the user presses the button so link previews cannot check a booking in.
 */
export default function CheckInPage() {
  const { roomId } = useParams<{ roomId: string }>()
  const [params] = useSearchParams()
  const method: CheckInMethod = params.get('method') === 'nfc' ? 'NFC' : 'QR'
  const [loaded, setLoaded] = useState<{ roomId: string; preview: CheckInPreview | null; error: string | null } | null>(null)
  const [result, setResult] = useState<CheckInResult | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!roomId) return
    let ignore = false
    getCheckInPreview(roomId).then(
      (preview) => { if (!ignore) setLoaded({ roomId, preview, error: null }) },
      (err: unknown) => { if (!ignore) setLoaded({ roomId, preview: null, error: formatApiError(err) }) },
    )
    return () => { ignore = true }
  }, [roomId])

  async function confirm() {
    if (!roomId) return
    setBusy(true)
    setError(null)
    try {
      setResult(await checkIn(roomId, method))
    } catch (err) {
      setError(formatApiError(err))
    } finally {
      setBusy(false)
    }
  }

  if (!roomId) return <main><p role="alert">Raum nicht gefunden.</p></main>
  if (!loaded || loaded.roomId !== roomId) return <main><p className="status-loading">Check-in wird geladen…</p></main>
  if (!loaded.preview) return <main><h1>Check-in</h1><p role="alert">{loaded.error}</p></main>

  const { preview } = loaded
  const booking = preview.reservation
  return (
    <main className="check-in-page">
      <h1>Check-in: {preview.roomName}</h1>
      {booking && (
        <section className="panel" aria-label="Buchung">
          <p>{booking.reservedFor}</p>
          <p>{formatTime(new Date(booking.startTime))} – {formatTime(new Date(booking.endTime))}</p>
        </section>
      )}

      {result ? (
        <>
          <p role="status">{result.alreadyActive ? 'Die Buchung ist bereits in Nutzung.' : 'Anwesenheit bestätigt – der Raum ist jetzt belegt.'}</p>
          {result.devices && <RoomDeviceIcons devices={result.devices} />}
          {result.failedDevices.length > 0 && (
            <p role="alert">
              Folgende Geräte konnten nicht geschaltet werden: {result.failedDevices.map((kind) => DEVICE_NAMES[kind]).join(', ')}
            </p>
          )}
        </>
      ) : (
        <>
          {preview.outcome === 'READY' && (
            <button type="button" onClick={() => void confirm()} disabled={busy}>Anwesenheit bestätigen</button>
          )}
          {preview.outcome === 'ALREADY_ACTIVE' && <p role="status">Die Buchung ist bereits in Nutzung.</p>}
          {preview.outcome === 'TOO_EARLY' && (preview.detail || preview.checkInOpensAt) && (
            <p role="status">
              {preview.detail ?? `Check-in ab ${formatTime(new Date(preview.checkInOpensAt as string))} möglich`}
            </p>
          )}
          {preview.outcome === 'EXPIRED' && (
            <p role="status">Die Buchung ist abgelaufen, weil nicht rechtzeitig eingecheckt wurde.</p>
          )}
          {preview.outcome === 'NO_MATCH' && (
            <p role="status">Für diesen Raum gibt es heute keine passende Buchung.</p>
          )}
        </>
      )}
      {error && <p role="alert">{error}</p>}
    </main>
  )
}

import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { getCheckInSettings, saveCheckInSettings } from '../API/checkInSettings'
import { formatApiError } from '../API/client'
import { checkInSettingsSchema } from '../types/checkInSettings'
import { formatDateTime } from '../utils/date'
import './AdminSettingsPage.css'

/** Admin settings (feature 014, FR-022): the check-in times, applied immediately to all bookings. */
export default function AdminSettingsPage() {
  const [early, setEarly] = useState('')
  const [grace, setGrace] = useState('')
  const [updatedAt, setUpdatedAt] = useState<string | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    let ignore = false
    getCheckInSettings().then(
      (settings) => {
        if (ignore) return
        setEarly(String(settings.earlyCheckInMinutes))
        setGrace(String(settings.gracePeriodMinutes))
        setUpdatedAt(settings.updatedAt)
      },
      (err: unknown) => { if (!ignore) setLoadError(formatApiError(err)) },
    )
    return () => { ignore = true }
  }, [])

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    setSaved(false)
    const parsed = checkInSettingsSchema.safeParse({
      earlyCheckInMinutes: early.trim() === '' ? NaN : Number(early),
      gracePeriodMinutes: grace.trim() === '' ? NaN : Number(grace),
    })
    if (!parsed.success) {
      setError(parsed.error.issues[0]?.message ?? 'Bitte die Eingaben prüfen.')
      return
    }
    setBusy(true)
    try {
      const result = await saveCheckInSettings(parsed.data)
      setEarly(String(result.earlyCheckInMinutes))
      setGrace(String(result.gracePeriodMinutes))
      setUpdatedAt(result.updatedAt)
      setSaved(true)
    } catch (err) {
      setError(formatApiError(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <main>
      <h1>Einstellungen</h1>
      {loadError ? (
        <p role="alert">{loadError}</p>
      ) : (
        <form className="panel check-in-settings" aria-labelledby="check-in-settings-title" onSubmit={(event) => void submit(event)} noValidate>
          <h2 id="check-in-settings-title">Check-in</h2>
          <p>Gilt sofort für alle Buchungen – für den Check-in per QR-Code, NFC und „Einchecken“ sowie für das automatische Ablaufen nicht eingecheckter Buchungen.</p>
          <label>
            Früher Check-in (Minuten vor Beginn)
            <input type="number" min={0} max={60} step={1} value={early} onChange={(event) => setEarly(event.target.value)} />
          </label>
          <p className="field-hint">0–60 Minuten; 0 schaltet den frühen Check-in aus. Nur möglich, wenn der Raum dann frei ist.</p>
          <label>
            Kulanzzeit (Minuten nach Beginn)
            <input type="number" min={1} max={30} step={1} value={grace} onChange={(event) => setGrace(event.target.value)} />
          </label>
          <p className="field-hint">1–30 Minuten; danach läuft eine nicht eingecheckte Buchung ab.</p>
          <button type="submit" disabled={busy}>Speichern</button>
          {saved && <p role="status">Gespeichert. Die neuen Zeiten gelten ab sofort.</p>}
          {error && <p role="alert">{error}</p>}
          {updatedAt && <p className="field-hint">Zuletzt geändert: {formatDateTime(updatedAt)}</p>}
        </form>
      )}
    </main>
  )
}

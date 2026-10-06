import { useCallback, useEffect, useState } from 'react'
import { ApiError, formatApiError } from '../../API/client'
import { getRoomDeviceControls, setRoomDeviceState } from '../../API/roomDevices'
import type { RoomDevice, RoomDeviceKind } from '../../types/roomDevice'
import './RoomDeviceControls.css'

const labels: Record<RoomDeviceKind, string> = { LIGHTING: 'Beleuchtung', VENTILATION: 'Lüftung', PROJECTOR: 'Projector' }

export function RoomDeviceControls({ roomId }: { roomId: string }) {
  const [devices, setDevices] = useState<RoomDevice[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [forbidden, setForbidden] = useState(false)
  const [busy, setBusy] = useState<RoomDeviceKind | null>(null)

  const load = useCallback(async () => {
    setLoading(true); setError(null); setForbidden(false)
    try { setDevices((await getRoomDeviceControls(roomId)).devices) }
    catch (err) { if (err instanceof ApiError && (err.status === 401 || err.status === 403)) setForbidden(true); else setError(formatApiError(err)) }
    finally { setLoading(false) }
  }, [roomId])
  useEffect(() => { queueMicrotask(() => void load()) }, [load])
  async function toggle(device: RoomDevice) {
    setBusy(device.kind); setError(null)
    try { const updated = await setRoomDeviceState(roomId, device.kind, { state: !device.state }); setDevices((prev) => prev.map((d) => d.kind === updated.kind ? updated : d)) }
    catch (err) { setError(formatApiError(err)); if (err instanceof ApiError && [401, 403, 409].includes(err.status)) void load() }
    finally { setBusy(null) }
  }
  if (loading) return <section className="panel" aria-label="Gerätesteuerung"><p>Gerätesteuerung wird geladen…</p></section>
  if (forbidden) return <section className="panel" aria-label="Gerätesteuerung"><p role="alert">Die Steuerung steht nur während Ihrer aktiven Reservierung zur Verfügung.</p></section>
  if (error && devices.length === 0) return <section className="panel" aria-label="Gerätesteuerung"><p role="alert">{error}</p><button type="button" onClick={() => void load()}>Erneut versuchen</button></section>
  return <section className="panel room-device-controls" aria-label="Gerätesteuerung"><h2>Gerätesteuerung</h2>{error && <p role="alert">{error}</p>}<div className="room-device-grid">{devices.map((device) => <article key={device.kind} className="room-device-card"><h3>{labels[device.kind]}</h3><p aria-live="polite">{device.state ? 'An' : 'Aus'}</p><button type="button" aria-pressed={device.state} disabled={!device.enabled || busy !== null} onClick={() => void toggle(device)}>{device.state ? `${labels[device.kind]} ausschalten` : `${labels[device.kind]} einschalten`}</button></article>)}</div>{devices.every((d) => d.kind !== 'PROJECTOR') && <p className="device-unavailable">Für diesen Raum ist kein Beamer eingerichtet.</p>}</section>
}

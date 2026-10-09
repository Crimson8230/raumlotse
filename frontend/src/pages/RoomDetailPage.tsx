import { useCallback, useEffect, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { useAdminMode } from '../auth/useAdminMode'
import { getRoom } from '../API/rooms'
import { getRoomStatus } from '../API/roomStatus'
import { ReservationForm } from '../components/ReservationForm/ReservationForm'
import { ReservationList } from '../components/ReservationList/ReservationList'
import { CheckInQrCode } from '../components/CheckInQrCode/CheckInQrCode'
import { RoomDeviceControlsDialog } from '../components/RoomDeviceControls/RoomDeviceControlsDialog'
import { PresenceSimulateButton } from '../components/PresenceSimulateButton/PresenceSimulateButton'
import { checkInLink } from '../utils/checkInLink'
import type { Room } from '../types/room'
import type { Reservation } from '../types/reservation'
import './RoomDetailPage.css'
import { entityStatusLabel } from '../utils/labels'

/** `?start=&end=` from a room search (contracts/ui.md §3); null unless both are valid instants and end > start. */
function bookingWindow(start: string | null, end: string | null): { start: string; end: string } | null {
  if (!start || !end) return null
  const startMs = Date.parse(start)
  const endMs = Date.parse(end)
  if (isNaN(startMs) || isNaN(endMs) || endMs <= startMs) return null
  return { start, end }
}

export default function RoomDetailPage() {
  const { roomId } = useParams<{ roomId: string }>()
  const { adminMode } = useAdminMode()
  const [searchParams] = useSearchParams()
  const requestedStart = searchParams.get('start')
  const requestedEnd = searchParams.get('end')
  const bookRequested = searchParams.get('book') === 'true'
  const [prefill, setPrefill] = useState<{ start: string; end: string } | null>(null)
  const [room, setRoom] = useState<Room | undefined>(undefined)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [showBookingForm, setShowBookingForm] = useState(false)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)
  const [refreshSignal, setRefreshSignal] = useState(0)
  const [nfcCopied, setNfcCopied] = useState(false)
  const [deviceDialogOpen, setDeviceDialogOpen] = useState(false)
  const [lastPresence, setLastPresence] = useState<{ roomId: string; at: string | null } | null>(null)

  // Administrators see when someone was last detected in the room (feature 014, FR-019).
  useEffect(() => {
    if (!roomId || !adminMode) return
    let ignore = false
    getRoomStatus(roomId).then(
      (status) => { if (!ignore) setLastPresence({ roomId, at: status.lastPresenceAt }) },
      () => { if (!ignore) setLastPresence({ roomId, at: null }) },
    )
    return () => { ignore = true }
  }, [roomId, adminMode])

  useEffect(() => {
    if (!roomId) return
    let ignore = false
    getRoom(roomId).then(
      (loaded) => {
        if (!ignore) {
          setRoom(loaded)
          // Decided only once the room has loaded, so a deactivated room never opens the form (FR-011a).
          const window = bookingWindow(requestedStart, requestedEnd)
          if ((window || bookRequested) && loaded.status === 'ACTIVE') {
            setPrefill(window)
            setShowBookingForm(true)
          }
          setLoading(false)
        }
      },
      () => {
        if (!ignore) {
          setLoadError('Dieser Raum wurde nicht gefunden.')
          setLoading(false)
        }
      },
    )
    return () => {
      ignore = true
    }
  }, [roomId, requestedStart, requestedEnd, bookRequested])

  const closeDeviceDialog = useCallback(() => setDeviceDialogOpen(false), [])

  function handleReservationSaved(reservation: Reservation) {
    setSuccessMessage(`Reservierung erfolgreich durch ${reservation.createdBy} gebucht.`)
    setShowBookingForm(false)
    setPrefill(null)
    setRefreshSignal((prev) => prev + 1)
  }

  if (loading) {
    return (
      <main>
        <p className="status-loading">Wird geladen…</p>
      </main>
    )
  }

  if (loadError || !room) {
    return (
      <main>
        <p role="alert" className="feedback-error">
          {loadError ?? 'Raum nicht gefunden.'}
        </p>
      </main>
    )
  }

  return (
    <main className="room-detail-page">
      <div className="room-detail-header">
        <h1>{room.name}</h1>
        <div>
          <Link to={`/rooms/${room.id}/display`}>Raumanzeige</Link>{' '}
          <button type="button" onClick={() => setDeviceDialogOpen(true)}>Geräte steuern</button>{' '}
          {adminMode && <Link to={`/admin/rooms/${room.id}/edit`}>Raum bearbeiten</Link>}
        </div>
      </div>

      {deviceDialogOpen && (
        <RoomDeviceControlsDialog roomId={room.id} roomName={room.name} onClose={closeDeviceDialog} />
      )}

      <dl className="panel room-detail-metadata">
        <dt>Gebäude</dt>
        <dd>{room.building.name}</dd>
        <dt>Stockwerk</dt>
        <dd>{room.floor.name}</dd>
        <dt>Status</dt>
        <dd className={room.status === 'ACTIVE' ? 'status-active' : 'status-deactivated'}>
          {entityStatusLabel(room.status)}
        </dd>
        <dt>Sitzordnungen</dt>
        <dd>
          {room.seatingArrangements.map((s) => `${s.name} (max ${s.maxCapacity})`).join(', ')}
        </dd>
        <dt>Barrierefrei erreichbar</dt>
        <dd>{room.barrierFreeReachable ? 'Ja' : 'Nein'}</dd>
      </dl>

      {adminMode && (
        <section className="panel room-check-in-codes" aria-labelledby="room-check-in-codes-title">
          <h2 id="room-check-in-codes-title">Check-in-Codes</h2>
          <p>Zum Aushängen am Raum: Nutzer:innen scannen den Code und bestätigen ihre Anwesenheit.</p>
          <CheckInQrCode link={checkInLink(room.id, 'qr')} roomName={room.name} />
          <button type="button" onClick={() => window.print()}>Drucken</button>
          <p>
            NFC-Link (als URL-Eintrag auf einen beliebigen NFC-Tag schreiben):{' '}
            <code>{checkInLink(room.id, 'nfc')}</code>
          </p>
          <button
            type="button"
            onClick={() => {
              void navigator.clipboard.writeText(checkInLink(room.id, 'nfc')).then(() => setNfcCopied(true))
            }}
          >
            NFC-Link kopieren
          </button>
          {nfcCopied && <span role="status">Kopiert</span>}
          <PresenceSimulateButton
            roomId={room.id}
            lastPresenceAt={lastPresence?.roomId === room.id ? lastPresence.at : null}
          />
        </section>
      )}

      {successMessage && (
        <p role="status" className="feedback-success">
          {successMessage}
        </p>
      )}

      <section className="room-reservations-section">
        <div className="room-reservations-header">
          <h2>Reservierungen</h2>
          {room.status === 'ACTIVE' && (
            <button
              type="button"
              onClick={() => {
                setSuccessMessage(null)
                setShowBookingForm((prev) => !prev)
              }}
            >
              {showBookingForm ? 'Buchungsformular schließen' : 'Raum buchen'}
            </button>
          )}
        </div>

        {showBookingForm && room.status === 'ACTIVE' && (
          <ReservationForm
            room={room}
            onSaved={handleReservationSaved}
            onCancel={() => setShowBookingForm(false)}
            initialStartTime={prefill?.start}
            initialEndTime={prefill?.end}
          />
        )}

        <ReservationList room={room} refreshSignal={refreshSignal} />
      </section>
    </main>
  )
}

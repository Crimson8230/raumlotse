import { useCallback, useEffect, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { useAdminMode } from '../auth/useAdminMode'
import { getRoom } from '../API/rooms'
import { listRoomReservations } from '../API/reservations'
import { listEquipmentTypes } from '../API/equipmentTypes'
import { getRoomStatus } from '../API/roomStatus'
import { listFavoriteRooms } from '../API/favoriteRooms'
import { FavoriteToggle } from '../components/FavoriteToggle/FavoriteToggle'
import { ReservationForm } from '../components/ReservationForm/ReservationForm'
import { ReservationList } from '../components/ReservationList/ReservationList'
import { RoomOverview } from '../components/RoomOverview/RoomOverview'
import { RoomDayOccupancy } from '../components/RoomDayOccupancy/RoomDayOccupancy'
import { CheckInQrCode } from '../components/CheckInQrCode/CheckInQrCode'
import { RoomDeviceControlsDialog } from '../components/RoomDeviceControls/RoomDeviceControlsDialog'
import { PresenceSimulateButton } from '../components/PresenceSimulateButton/PresenceSimulateButton'
import { checkInLink } from '../utils/checkInLink'
import type { Room } from '../types/room'
import type { Reservation } from '../types/reservation'
import './RoomDetailPage.css'
import { entityStatusLabel } from '../utils/labels'
import { ErrorMessage, RoomName } from '../components/ui'
import { formatApiError } from '../API/client'

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
  const { adminMode, permissions } = useAdminMode()
  const [searchParams] = useSearchParams()
  const requestedStart = searchParams.get('start')
  const requestedEnd = searchParams.get('end')
  const [prefill, setPrefill] = useState<{ start: string; end: string } | null>(null)
  const [room, setRoom] = useState<Room | undefined>(undefined)
  const [favoriteIds, setFavoriteIds] = useState<Set<string>>(new Set())
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [formKey, setFormKey] = useState(0)
  const [reservations, setReservations] = useState<Reservation[] | undefined>(undefined)
  const [reservationsError, setReservationsError] = useState<string | null>(null)
  const [equipmentNames, setEquipmentNames] = useState<Record<string, string>>({})
  const [now, setNow] = useState(() => new Date())
  const [successMessage, setSuccessMessage] = useState<string | null>(null)
  const [refreshSignal, setRefreshSignal] = useState(0)

  // One reservation query feeds "Meine Buchungen", the day occupancy, the management list and device eligibility.
  useEffect(() => {
    if (!roomId) return
    let ignore = false
    listRoomReservations(roomId).then(
      (entries) => {
        if (ignore) return
        setReservations(entries)
        setReservationsError(null)
      },
      (err) => {
        if (ignore) return
        setReservations([])
        setReservationsError(formatApiError(err))
      },
    )
    return () => { ignore = true }
  }, [roomId, refreshSignal])

  useEffect(() => {
    let ignore = false
    listEquipmentTypes('all').then(
      (types) => { if (!ignore) setEquipmentNames(Object.fromEntries(types.map((t) => [t.id, t.name]))) },
      () => {},
    )
    return () => { ignore = true }
  }, [])

  useEffect(() => {
    const timer = window.setInterval(() => setNow(new Date()), 60_000)
    return () => window.clearInterval(timer)
  }, [])

  const deviceEligible =
    permissions.includes('OWN_ACTIVE_DEVICE_CONTROL') &&
    (reservations ?? []).some((entry) => entry.ownedByMe && entry.status === 'ACTIVE')
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

  // Favorites only drive the star; if they cannot be loaded the page still works without it (feature 017).
  useEffect(() => {
    let ignore = false
    listFavoriteRooms().then(
      (loaded) => {
        if (!ignore) setFavoriteIds(new Set(loaded.map((favorite) => favorite.roomId)))
      },
      () => {},
    )
    return () => {
      ignore = true
    }
  }, [])

  const handleFavoriteChanged = useCallback((changedRoomId: string, favorite: boolean) => {
    setFavoriteIds((current) => {
      const next = new Set(current)
      if (favorite) next.add(changedRoomId)
      else next.delete(changedRoomId)
      return next
    })
  }, [])

  useEffect(() => {
    if (!roomId) return
    let ignore = false
    getRoom(roomId).then(
      (loaded) => {
        if (!ignore) {
          setRoom(loaded)
          // Decided only once the room has loaded, so a deactivated room never opens the form (FR-011a).
          const window = bookingWindow(requestedStart, requestedEnd)
          if (window && loaded.status === 'ACTIVE') setPrefill(window)
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
  }, [roomId, requestedStart, requestedEnd])

  const closeDeviceDialog = useCallback(() => setDeviceDialogOpen(false), [])

  function handleReservationSaved(reservation: Reservation) {
    setSuccessMessage(`Reservierung erfolgreich durch ${reservation.createdBy} gebucht.`)
    setPrefill(null)
    setFormKey((prev) => prev + 1)
    setRefreshSignal((prev) => prev + 1)
  }

  if (loading) {
    return (
      <main id="inhalt">
        <p className="status-loading">Wird geladen…</p>
      </main>
    )
  }

  if (loadError || !room) {
    return (
      <main id="inhalt">
        <ErrorMessage>{loadError ?? 'Raum nicht gefunden.'}</ErrorMessage>
      </main>
    )
  }

  const canReserve = room.status === 'ACTIVE' && permissions.includes('RESERVE')

  return (
    <main id="inhalt" className="room-detail-page">
      <div className="room-detail-header">
        <div className="room-detail-title">
          <h1><RoomName>{room.name}</RoomName></h1>
          {room.status === 'ACTIVE' && (
            <FavoriteToggle
              roomId={room.id}
              roomName={room.name}
              isFavorite={favoriteIds.has(room.id)}
              onChanged={handleFavoriteChanged}
            />
          )}
        </div>
        <div>
          <Link to={`/rooms/${room.id}/display`}>Raumanzeige</Link>{' '}
          {deviceEligible && (
            <button type="button" onClick={() => setDeviceDialogOpen(true)}>Geräte steuern</button>
          )}{' '}
          {adminMode && permissions.includes('ROOM_MANAGE') && <Link to={`/admin/rooms/${room.id}/edit`}>Raum bearbeiten</Link>}
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
      </dl>

      {successMessage && (
        <p role="status" className="feedback-success">
          {successMessage}
        </p>
      )}

      {reservationsError && <ErrorMessage>{reservationsError}</ErrorMessage>}

      <div className="room-detail-grid">
        <section className="room-detail-mine" aria-labelledby="room-mine-title">
          <h2 id="room-mine-title">Meine Buchungen</h2>
          <ReservationList
            room={room}
            scope="mine"
            reservations={reservations ?? []}
            onReservationChanged={() => setRefreshSignal((prev) => prev + 1)}
          />
        </section>

        {canReserve && (
          <section className="room-detail-form" aria-labelledby="room-book-title">
            <h2 id="room-book-title">Raum buchen</h2>
            <ReservationForm
              key={formKey}
              room={room}
              onSaved={handleReservationSaved}
              initialStartTime={prefill?.start}
              initialEndTime={prefill?.end}
            />
          </section>
        )}

        <div className="room-detail-day">
          <RoomDayOccupancy reservations={reservations ?? []} now={now} />
        </div>
      </div>

      {permissions.includes('OTHER_RESERVATION_MANAGE') && (
        <section className="room-reservations-section" aria-labelledby="room-managed-title">
          <h2 id="room-managed-title">Weitere Reservierungen</h2>
          <ReservationList
            room={room}
            scope="managed"
            reservations={reservations ?? []}
            onReservationChanged={() => setRefreshSignal((prev) => prev + 1)}
          />
        </section>
      )}

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

      <RoomOverview room={room} equipmentNames={equipmentNames} />
    </main>
  )
}

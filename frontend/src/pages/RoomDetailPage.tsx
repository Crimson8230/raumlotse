import { useEffect, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { getRoom } from '../API/rooms'
import { ReservationForm } from '../components/ReservationForm/ReservationForm'
import { ReservationList } from '../components/ReservationList/ReservationList'
import type { Room } from '../types/room'
import type { Reservation } from '../types/reservation'
import './RoomDetailPage.css'

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
          setLoadError('This room could not be found.')
          setLoading(false)
        }
      },
    )
    return () => {
      ignore = true
    }
  }, [roomId, requestedStart, requestedEnd, bookRequested])

  function handleReservationSaved(reservation: Reservation) {
    setSuccessMessage(`Reservation successfully booked by ${reservation.createdBy}!`)
    setShowBookingForm(false)
    setPrefill(null)
    setRefreshSignal((prev) => prev + 1)
  }

  if (loading) {
    return (
      <main>
        <p className="status-loading">Loading…</p>
      </main>
    )
  }

  if (loadError || !room) {
    return (
      <main>
        <p role="alert" className="feedback-error">
          {loadError ?? 'Room not found.'}
        </p>
      </main>
    )
  }

  return (
    <main className="room-detail-page">
      <div className="room-detail-header">
        <h1>{room.name}</h1>
        <div>
          <Link to={`/rooms/${room.id}/display`}>Display Room Information</Link>{' '}
          <Link to={`/rooms/${room.id}/control`}>Control Devices</Link>{' '}
          <Link to={`/rooms/${room.id}/edit`}>Edit Room</Link>
        </div>
      </div>

      <dl className="panel room-detail-metadata">
        <dt>Building</dt>
        <dd>{room.building.name}</dd>
        <dt>Floor</dt>
        <dd>{room.floor.name}</dd>
        <dt>Status</dt>
        <dd className={room.status === 'ACTIVE' ? 'status-active' : 'status-deactivated'}>
          {room.status}
        </dd>
        <dt>Seating Arrangements</dt>
        <dd>
          {room.seatingArrangements.map((s) => `${s.name} (max ${s.maxCapacity})`).join(', ')}
        </dd>
        <dt>Barrierefrei erreichbar</dt>
        <dd>{room.barrierFreeReachable ? 'Ja' : 'Nein'}</dd>
      </dl>

      {successMessage && (
        <p role="status" className="feedback-success">
          {successMessage}
        </p>
      )}

      <section className="room-reservations-section">
        <div className="room-reservations-header">
          <h2>Reservations</h2>
          {room.status === 'ACTIVE' && (
            <button
              type="button"
              onClick={() => {
                setSuccessMessage(null)
                setShowBookingForm((prev) => !prev)
              }}
            >
              {showBookingForm ? 'Close Booking Form' : 'Book Room'}
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

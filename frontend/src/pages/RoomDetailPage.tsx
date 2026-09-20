import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { getRoom } from '../API/rooms'
import { ReservationForm } from '../components/ReservationForm/ReservationForm'
import { ReservationList } from '../components/ReservationList/ReservationList'
import type { Room } from '../types/room'
import type { Reservation } from '../types/reservation'
import './RoomDetailPage.css'

export default function RoomDetailPage() {
  const { roomId } = useParams<{ roomId: string }>()
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
  }, [roomId])

  function handleReservationSaved(reservation: Reservation) {
    setSuccessMessage(`Reservation successfully booked by ${reservation.createdBy}!`)
    setShowBookingForm(false)
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
        <Link to={`/rooms/${room.id}/edit`}>Edit Room</Link>
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
          />
        )}

        <ReservationList room={room} refreshSignal={refreshSignal} />
      </section>
    </main>
  )
}

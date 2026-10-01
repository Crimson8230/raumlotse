import { formatDate, formatTime } from '../../utils/date'
import { getNotePreview } from './roomDisplayLogic'
import type { RoomDisplayProps, RoomDisplayStatus } from './roomDisplayTypes'
import './RoomDisplay.css'

const STATUS_LABELS: Record<RoomDisplayStatus, string> = {
  AVAILABLE: 'Available',
  RESERVED: 'Reserved',
  OCCUPIED: 'Reserved and Occupied',
  UNAVAILABLE: 'Unavailable',
}

export function RoomDisplay({
  roomName,
  currentDateTime,
  reservation,
  status,
  nextReservation,
  state,
  errorMessage,
}: RoomDisplayProps) {
  return (
    <main
      className="room-display"
      data-testid="room-display"
    >
      {state !== 'unavailable' && (
        <p
          role="status"
          aria-label={STATUS_LABELS[status]}
          className={`room-display-status room-display-status-${status.toLowerCase()}`}
        >
          {STATUS_LABELS[status]}
        </p>
      )}

      <header className="room-display-header">
        <h1>{roomName}</h1>
        <p className="room-display-current-time" aria-label="Current date and time">
          {formatDate(currentDateTime)}, {formatTime(currentDateTime)}
        </p>
      </header>

      {state === 'unavailable' && (
        <p role="alert" className="room-display-state room-display-state-error">
          {errorMessage ?? 'Room information is unavailable.'}
        </p>
      )}

      {state === 'no-reservation' && (
        <p role="status" aria-label="No current reservation" className="room-display-state">
          No current reservation
        </p>
      )}

      {state === 'reservation' && reservation && (
        <section className="room-display-reservation" aria-labelledby="room-display-reservation-title">
          <h2 id="room-display-reservation-title">Current reservation</h2>
          <p className="room-display-booked-by">Booked by: {reservation.createdBy}</p>
          <p className="room-display-reserved-for">Reserved for: {reservation.reservedFor}</p>
          <dl className="room-display-details">
            <dt>Note</dt>
            <dd className="room-display-note" aria-label="Reservation note">
              {getNotePreview(reservation.note).text}
            </dd>
            <dt>Start time</dt>
            <dd>{formatTime(new Date(reservation.startTime))}</dd>
            <dt>End time</dt>
            <dd>{formatTime(new Date(reservation.endTime))}</dd>
          </dl>
        </section>
      )}

      {state !== 'unavailable' && nextReservation && (
        <section className="room-display-next-reservation" aria-labelledby="room-display-next-title">
          <p id="room-display-next-title" className="room-display-next-reservation-line" data-testid="room-display-next-reservation-line">
            <span>Next Reservation</span>
            <span>Reserved for: {nextReservation.reservedFor?.trim() || 'Not specified'}</span>
            <span>Start Time</span>
            <span>{formatTime(new Date(nextReservation.startTime))}</span>
            <span>End Time</span>
            <span>{formatTime(new Date(nextReservation.endTime))}</span>
          </p>
        </section>
      )}

      {state !== 'unavailable' && !nextReservation && (
        <p role="status" aria-label="No next reservation scheduled" className="room-display-state">
          No next reservation scheduled
        </p>
      )}

    </main>
  )
}

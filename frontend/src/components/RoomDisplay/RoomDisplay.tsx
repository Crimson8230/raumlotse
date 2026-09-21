import { formatDate, formatTime } from '../../utils/date'
import { getNotePreview } from './roomDisplayLogic'
import type { RoomDisplayProps } from './roomDisplayTypes'
import './RoomDisplay.css'

export function RoomDisplay({
  roomName,
  currentDateTime,
  reservation,
  upcomingReservation = null,
  state,
  errorMessage,
}: RoomDisplayProps) {
  return (
    <main
      className={`room-display${upcomingReservation ? ' room-display--has-upcoming' : ''}`}
      data-testid="room-display"
    >
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

      {upcomingReservation && (
        <section className="room-display-upcoming" aria-labelledby="room-display-upcoming-title">
          <h2 id="room-display-upcoming-title">Upcoming reservation</h2>
          <p className="room-display-upcoming-times" aria-label="Upcoming reservation times">
            {formatTime(new Date(upcomingReservation.startTime))} –{' '}
            {formatTime(new Date(upcomingReservation.endTime))}
          </p>
        </section>
      )}
    </main>
  )
}

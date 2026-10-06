import { formatDate, formatTime } from '../../utils/date'
import { getNotePreview } from './roomDisplayLogic'
import type { RoomDisplayProps, RoomDisplayStatus } from './roomDisplayTypes'
import './RoomDisplay.css'

const STATUS_LABELS: Record<RoomDisplayStatus, string> = {
  AVAILABLE: 'Frei',
  RESERVED: 'Reserviert',
  OCCUPIED: 'Belegt',
  UNAVAILABLE: 'Nicht verfügbar',
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
        <p className="room-display-current-time" aria-label="Aktuelles Datum und Uhrzeit">
          {formatDate(currentDateTime)}, {formatTime(currentDateTime)}
        </p>
      </header>

      {state === 'unavailable' && (
        <p role="alert" className="room-display-state room-display-state-error">
          {errorMessage ?? 'Rauminformationen sind nicht verfügbar.'}
        </p>
      )}

      {state === 'no-reservation' && (
        <p role="status" aria-label="Keine aktuelle Reservierung" className="room-display-state">
          Keine aktuelle Reservierung
        </p>
      )}

      {state === 'reservation' && reservation && (
        <section className="room-display-reservation" aria-labelledby="room-display-reservation-title">
          <h2 id="room-display-reservation-title">Aktuelle Reservierung</h2>
          {reservation.createdBy && <p className="room-display-booked-by">Gebucht von: {reservation.createdBy}</p>}
          {reservation.reservedFor && <p className="room-display-reserved-for">Reserviert für: {reservation.reservedFor}</p>}
          <dl className="room-display-details">
            {/* A redacted entry (somebody else's booking) carries no creator and therefore no note either. */}
            {reservation.createdBy !== null && (
              <>
                <dt>Notiz</dt>
                <dd className="room-display-note" aria-label="Reservierungsnotiz">
                  {getNotePreview(reservation.note).text}
                </dd>
              </>
            )}
            <dt>Beginn</dt>
            <dd>{formatTime(new Date(reservation.startTime))}</dd>
            <dt>Ende</dt>
            <dd>{formatTime(new Date(reservation.endTime))}</dd>
          </dl>
        </section>
      )}

      {state !== 'unavailable' && nextReservation && (
        <section className="room-display-next-reservation" aria-labelledby="room-display-next-title">
          <p id="room-display-next-title" className="room-display-next-reservation-line" data-testid="room-display-next-reservation-line">
            <span>Nächste Reservierung</span>
            {nextReservation.reservedFor !== null && (
              <span>Reserviert für: {nextReservation.reservedFor?.trim() || 'Nicht angegeben'}</span>
            )}
            <span>Beginn</span>
            <span>{formatTime(new Date(nextReservation.startTime))}</span>
            <span>Ende</span>
            <span>{formatTime(new Date(nextReservation.endTime))}</span>
          </p>
        </section>
      )}

      {state !== 'unavailable' && !nextReservation && (
        <p role="status" aria-label="Keine weitere Reservierung geplant" className="room-display-state">
          Keine weitere Reservierung geplant
        </p>
      )}

    </main>
  )
}

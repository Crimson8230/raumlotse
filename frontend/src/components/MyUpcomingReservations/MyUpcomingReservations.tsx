import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getMyUpcomingReservations } from '../../API/reservations'
import { formatDateTimeRange, formatDuration } from '../../utils/date'
import type { Reservation } from '../../types/reservation'
import './MyUpcomingReservations.css'

export function MyUpcomingReservations() {
  const [reservations, setReservations] = useState<Reservation[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true

    getMyUpcomingReservations()
      .then((data) => {
        if (active) {
          setReservations(data)
          setLoading(false)
        }
      })
      .catch((err) => {
        if (active) {
          setError(err instanceof Error ? err.message : 'Fehler beim Laden der Reservierungen')
          setLoading(false)
        }
      })

    return () => {
      active = false
    }
  }, [])

  return (
    <section className="my-upcoming-reservations panel">
      <h2>Meine nächsten Reservierungen</h2>

      {loading && <p className="loading-state">Reservierungen werden geladen...</p>}

      {error && (
        <div role="alert" className="feedback-error">
          <p>{error}</p>
        </div>
      )}

      {!loading && !error && reservations.length === 0 && (
        <p className="empty-state">Keine anstehenden Reservierungen vorhanden.</p>
      )}

      {!loading && !error && reservations.length > 0 && (
        <div className="table-responsive">
          <table className="upcoming-reservations-table">
            <thead>
              <tr>
                <th>Zeitpunkt</th>
                <th>Raum</th>
                <th>Dauer</th>
              </tr>
            </thead>
            <tbody>
              {reservations.map((res) => (
                <tr key={res.id}>
                  <td>{formatDateTimeRange(res.startTime, res.endTime)}</td>
                  <td>
                    <Link to={`/rooms/${res.roomId}`}>{res.roomName}</Link>
                  </td>
                  <td>{formatDuration(res.startTime, res.endTime)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}

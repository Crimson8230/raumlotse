import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getMyUpcomingReservations } from '../../API/reservations'
import { formatDateTimeRange, formatDuration } from '../../utils/date'
import type { Reservation } from '../../types/reservation'
import './MyUpcomingReservations.css'
import { DataTable, ErrorMessage } from '../ui'

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

      {error && <ErrorMessage>{error}</ErrorMessage>}

      {!loading && !error && (
        <DataTable
          caption="Anstehende Reservierungen"
          columns={[
            {
              key: 'time',
              header: 'Zeitpunkt',
              mono: true,
              cell: (res) => formatDateTimeRange(res.startTime, res.endTime),
            },
            {
              key: 'room',
              header: 'Raum',
              cell: (res) => (
                <Link className="room-name" to={`/rooms/${res.roomId}`}>
                  {res.roomName}
                </Link>
              ),
            },
            { key: 'duration', header: 'Dauer', mono: true, cell: (res) => formatDuration(res.startTime, res.endTime) },
          ]}
          rows={reservations}
          rowKey={(res) => res.id}
          rowHeader="room"
          emptyText="Keine anstehenden Reservierungen vorhanden."
        />
      )}
    </section>
  )
}

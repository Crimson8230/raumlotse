import { Chip, DataList, Strip, TimeText } from '../ui'
import { dayRange, toStripWindows } from '../RoomList/dayWindows'
import { formatTime } from '../../utils/date'
import type { Reservation } from '../../types/reservation'
import './RoomDayOccupancy.css'

interface RoomDayOccupancyProps {
  reservations: Reservation[]
  now: Date
}

function overlapsDay(reservation: Reservation, now: Date): boolean {
  const dayStart = new Date(now)
  dayStart.setHours(0, 0, 0, 0)
  const dayEnd = new Date(dayStart)
  dayEnd.setDate(dayEnd.getDate() + 1)
  return new Date(reservation.startTime) < dayEnd && new Date(reservation.endTime) > dayStart
}

/** Belegungen des heutigen Tages: Streifen und kompakte, schreibgeschützte Liste (Zeit mono, Zustand als Wort). */
export function RoomDayOccupancy({ reservations, now }: RoomDayOccupancyProps) {
  const today = reservations
    .filter((r) => r.status !== 'CANCELLED' && r.status !== 'EXPIRED' && overlapsDay(r, now))
    .sort((a, b) => new Date(a.startTime).getTime() - new Date(b.startTime).getTime())
  const range = dayRange(now)

  return (
    <section className="room-day" aria-labelledby="room-day-title">
      <h2 id="room-day-title">Belegungen des Tages</h2>
      <Strip rangeStart={range.start} rangeEnd={range.end} windows={toStripWindows(today)} now={now} />
      <DataList
        aria-label="Belegungen des Tages"
        items={today}
        itemKey={(r) => r.id}
        emptyText="Heute keine Belegungen."
        renderItem={(r) => (
          <span className="room-day__line">
            <TimeText>{`${formatTime(new Date(r.startTime))}–${formatTime(new Date(r.endTime))}`}</TimeText>
            {r.ownedByMe ? <Chip state="meine" /> : <Chip state="belegt" />}
          </span>
        )}
      />
    </section>
  )
}

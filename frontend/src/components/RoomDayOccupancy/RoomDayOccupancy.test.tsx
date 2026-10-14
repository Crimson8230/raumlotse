import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { RoomDayOccupancy } from './RoomDayOccupancy'
import type { Reservation } from '../../types/reservation'

const now = new Date(2026, 9, 14, 10, 30)
const at = (hour: number, minute = 0) => new Date(2026, 9, 14, hour, minute).toISOString()

function reservation(overrides: Partial<Reservation> = {}): Reservation {
  return {
    id: 'a', roomId: 'r1', roomName: 'Raum 2.14', startTime: at(9), endTime: at(11), status: 'RESERVED',
    seatingArrangement: null, expectedAttendees: null, additionalEquipment: [], note: null, createdBy: null,
    reservedFor: null, createdAt: null, ownedByMe: false, ...overrides,
  }
}

describe('RoomDayOccupancy', () => {
  it('lists today\'s windows with time in mono and a state word', () => {
    render(<RoomDayOccupancy reservations={[reservation(), reservation({ id: 'b', ownedByMe: true, startTime: at(13), endTime: at(14, 30) })]} now={now} />)

    expect(screen.getByRole('heading', { name: 'Belegungen des Tages' })).toBeInTheDocument()
    const items = screen.getAllByRole('listitem')
    expect(items).toHaveLength(2)
    expect(items[0].querySelector('.mono')).toHaveTextContent('09:00–11:00')
    expect(items[0]).toHaveTextContent('belegt')
    expect(items[1].querySelector('.mono')).toHaveTextContent('13:00–14:30')
    expect(items[1]).toHaveTextContent('meine')
  })

  it('shows a strip for the day', () => {
    render(<RoomDayOccupancy reservations={[reservation()]} now={now} />)

    expect(screen.getByRole('img', { name: /belegt/ })).toBeInTheDocument()
  })

  it('ignores other days and cancelled or expired bookings', () => {
    render(<RoomDayOccupancy now={now} reservations={[
      reservation({ id: 'x', startTime: new Date(2026, 9, 15, 9).toISOString(), endTime: new Date(2026, 9, 15, 10).toISOString() }),
      reservation({ id: 'y', status: 'CANCELLED' }),
      reservation({ id: 'z', status: 'EXPIRED' }),
    ]} />)

    expect(screen.getByText('Heute keine Belegungen.')).toBeInTheDocument()
    expect(screen.queryByRole('listitem')).not.toBeInTheDocument()
  })
})

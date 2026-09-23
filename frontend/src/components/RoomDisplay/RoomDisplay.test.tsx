import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { RoomDisplay } from './RoomDisplay'
import type { Reservation } from '../../types/reservation'
import { formatDate, formatTime } from '../../utils/date'

function reservationFixture(): Reservation {
  return {
    id: 'res-1',
    roomId: 'room-1',
    roomName: 'Room 101',
    startTime: '2026-09-20T09:30:00.000Z',
    endTime: '2026-09-20T11:30:00.000Z',
    status: 'ACTIVE',
    seatingArrangement: { id: 'seat-1', name: 'Theater', maxCapacity: 40 },
    expectedAttendees: 20,
    additionalEquipment: [],
    note: 'Team meeting',
    createdBy: 'Alice',
    createdAt: '2026-09-19T09:00:00.000Z',
  }
}

describe('RoomDisplay smoke test', () => {
  it('renders the display heading for a room', () => {
    render(
      <RoomDisplay
        roomName="Room 101"
        currentDateTime={new Date('2026-09-20T10:00:00')}
        reservation={null}
        state="no-reservation"
      />,
    )

    expect(screen.getByRole('heading', { name: 'Room 101' })).toBeInTheDocument()
  })

  it('renders the required current reservation information', () => {
    render(
      <RoomDisplay
        roomName="Room 101"
        currentDateTime={new Date(2026, 8, 20, 10, 0)}
        reservation={reservationFixture()}
        state="reservation"
      />,
    )

    expect(screen.getByRole('heading', { name: 'Room 101' })).toBeInTheDocument()
    const currentDateTime = new Date(2026, 8, 20, 10, 0)
    expect(
      screen.getByText(`${formatDate(currentDateTime)}, ${formatTime(currentDateTime)}`),
    ).toBeInTheDocument()
    expect(screen.getByText('Team meeting')).toBeInTheDocument()
    expect(screen.getByText('Booked by: Alice')).toBeInTheDocument()
    expect(screen.getByText(formatTime(new Date(reservationFixture().startTime)))).toBeInTheDocument()
    expect(screen.getByText(formatTime(new Date(reservationFixture().endTime)))).toBeInTheDocument()
  })

  it('renders an explicit no-reservation state', () => {
    render(
      <RoomDisplay
        roomName="Room 101"
        currentDateTime={new Date(2026, 8, 20, 10, 0)}
        reservation={null}
        state="no-reservation"
      />,
    )

    expect(screen.getByRole('heading', { name: 'Room 101' })).toBeInTheDocument()
    expect(screen.getByRole('status', { name: 'No current reservation' })).toBeInTheDocument()
  })

  it('renders an explicit unavailable state without reservation details', () => {
    render(
      <RoomDisplay
        roomName="Room unavailable"
        currentDateTime={new Date(2026, 8, 20, 10, 0)}
        reservation={null}
        state="unavailable"
      />,
    )

    expect(screen.getByRole('alert')).toHaveTextContent(/unavailable/i)
    expect(screen.queryByRole('heading', { name: 'Current reservation' })).not.toBeInTheDocument()
  })

  it('keeps long notes readable with an explicit truncation marker', () => {
    render(
      <RoomDisplay
        roomName="Room 101"
        currentDateTime={new Date(2026, 8, 20, 10, 0)}
        reservation={{ ...reservationFixture(), note: 'A'.repeat(300) }}
        state="reservation"
      />,
    )

    expect(screen.getByTestId('room-display')).toHaveClass('room-display')
    expect(screen.getByText(/\.\.\.$/)).toBeInTheDocument()
    expect(screen.getByText(/Start time/)).toBeInTheDocument()
    expect(screen.getByText(/End time/)).toBeInTheDocument()
  })

})

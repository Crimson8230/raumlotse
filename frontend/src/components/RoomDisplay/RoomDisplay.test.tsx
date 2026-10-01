import { render, screen, within } from '@testing-library/react'
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
    reservedFor: 'Team Alpha',
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
        status="AVAILABLE"
        nextReservation={null}
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
        status="OCCUPIED"
        nextReservation={null}
      />,
    )

    expect(screen.getByRole('heading', { name: 'Room 101' })).toBeInTheDocument()
    const currentDateTime = new Date(2026, 8, 20, 10, 0)
    expect(
      screen.getByText(`${formatDate(currentDateTime)}, ${formatTime(currentDateTime)}`),
    ).toBeInTheDocument()
    expect(screen.getByText('Team meeting')).toBeInTheDocument()
    expect(screen.getByText('Booked by: Alice')).toBeInTheDocument()
    expect(screen.getByText('Reserved for: Team Alpha')).toBeInTheDocument()
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
        status="AVAILABLE"
        nextReservation={null}
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
        status="UNAVAILABLE"
        nextReservation={null}
      />,
    )

    expect(screen.getByRole('alert')).toHaveTextContent(/unavailable/i)
    expect(screen.queryByText('Available')).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Current reservation' })).not.toBeInTheDocument()
  })

  it('keeps long notes readable with an explicit truncation marker', () => {
    render(
      <RoomDisplay
        roomName="Room 101"
        currentDateTime={new Date(2026, 8, 20, 10, 0)}
        reservation={{ ...reservationFixture(), note: 'A'.repeat(300) }}
        state="reservation"
        status="OCCUPIED"
        nextReservation={null}
      />,
    )

    expect(screen.getByTestId('room-display')).toHaveClass('room-display')
    expect(screen.getByText(/\.\.\.$/)).toBeInTheDocument()
    expect(screen.getByText(/Start time/)).toBeInTheDocument()
    expect(screen.getByText(/End time/)).toBeInTheDocument()
  })

  it.each([
    ['AVAILABLE', 'Available'],
    ['RESERVED', 'Reserved'],
    ['OCCUPIED', 'Reserved and Occupied'],
  ] as const)('renders the %s status with a text label', (status, label) => {
    render(
      <RoomDisplay
        roomName="Room 101"
        currentDateTime={new Date(2026, 8, 20, 10, 0)}
        reservation={status === 'OCCUPIED' ? reservationFixture() : null}
        state={status === 'OCCUPIED' ? 'reservation' : 'no-reservation'}
        status={status}
        nextReservation={null}
      />,
    )

    expect(screen.getByRole('status', { name: label })).toBeInTheDocument()
    expect(screen.getByText(label)).toBeInTheDocument()
  })

  it('renders the room status above the current date and time', () => {
    render(
      <RoomDisplay
        roomName="Room 101"
        currentDateTime={new Date(2026, 8, 20, 10, 0)}
        reservation={null}
        state="no-reservation"
        status="AVAILABLE"
        nextReservation={null}
      />,
    )

    const status = screen.getByRole('status', { name: 'Available' })
    const currentDateTime = screen.getByLabelText('Current date and time')

    expect(status.compareDocumentPosition(currentDateTime) & Node.DOCUMENT_POSITION_FOLLOWING).toBe(
      Node.DOCUMENT_POSITION_FOLLOWING,
    )
  })

  it('renders the next reservation with required labels and values', () => {
    const nextReservation = {
      ...reservationFixture(),
      id: 'next',
      status: 'RESERVED' as const,
      startTime: '2026-09-20T11:00:00.000Z',
      endTime: '2026-09-20T12:00:00.000Z',
      reservedFor: 'Next Team',
    }

    render(
      <RoomDisplay
        roomName="Room 101"
        currentDateTime={new Date(2026, 8, 20, 10, 0)}
        reservation={null}
        state="no-reservation"
        status="RESERVED"
        nextReservation={nextReservation}
      />,
    )

    expect(screen.getByText('Next Reservation')).toBeInTheDocument()
    expect(screen.getByText('Reserved for: Next Team')).toBeInTheDocument()
    const nextReservationLine = screen.getByTestId('room-display-next-reservation-line')
    expect(within(nextReservationLine).getByText('Start Time')).toBeInTheDocument()
    expect(within(nextReservationLine).getByText('End Time')).toBeInTheDocument()
  })

  it('renders the complete next reservation as one line', () => {
    const nextReservation = {
      ...reservationFixture(),
      id: 'next',
      status: 'RESERVED' as const,
      startTime: '2026-09-20T11:00:00.000Z',
      endTime: '2026-09-20T12:00:00.000Z',
      reservedFor: 'Next Team',
    }

    render(
      <RoomDisplay
        roomName="Room 101"
        currentDateTime={new Date(2026, 8, 20, 10, 0)}
        reservation={null}
        state="no-reservation"
        status="RESERVED"
        nextReservation={nextReservation}
      />,
    )

    const line = screen.getByTestId('room-display-next-reservation-line')
    expect(line).toHaveTextContent('Next Reservation')
    expect(line).toHaveTextContent('Reserved for: Next Team')
    expect(within(line).getByText('Start Time')).toBeInTheDocument()
    expect(within(line).getByText('End Time')).toBeInTheDocument()
    expect(line.tagName).toBe('P')
  })

  it('renders an explicit next-reservation fallback for missing data', () => {
    render(
      <RoomDisplay
        roomName="Room 101"
        currentDateTime={new Date(2026, 8, 20, 10, 0)}
        reservation={null}
        state="no-reservation"
        status="AVAILABLE"
        nextReservation={null}
      />,
    )

    expect(screen.getByRole('status', { name: 'No next reservation scheduled' })).toBeInTheDocument()
  })

  it('uses a visible fallback when reservedFor is blank', () => {
    const nextReservation = { ...reservationFixture(), status: 'RESERVED' as const, reservedFor: '' }

    render(
      <RoomDisplay
        roomName="Room 101"
        currentDateTime={new Date(2026, 8, 20, 10, 0)}
        reservation={null}
        state="no-reservation"
        status="RESERVED"
        nextReservation={nextReservation}
      />,
    )

    expect(screen.getByText('Reserved for: Not specified')).toBeInTheDocument()
  })

})

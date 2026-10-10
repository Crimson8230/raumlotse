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
    ownedByMe: true,
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
    expect(screen.getByText('Gebucht von: Alice')).toBeInTheDocument()
    expect(screen.getByText('Reserviert für: Team Alpha')).toBeInTheDocument()
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
    expect(screen.getByRole('status', { name: 'Keine aktuelle Reservierung' })).toBeInTheDocument()
  })

  it('renders an explicit unavailable state without reservation details', () => {
    render(
      <RoomDisplay
        roomName="Raum nicht verfügbar"
        currentDateTime={new Date(2026, 8, 20, 10, 0)}
        reservation={null}
        state="unavailable"
        status="UNAVAILABLE"
        nextReservation={null}
      />,
    )

    expect(screen.getByRole('alert')).toHaveTextContent(/nicht verfügbar/i)
    expect(screen.queryByText('Verfügbar')).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Aktuelle Reservierung' })).not.toBeInTheDocument()
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
    expect(screen.getByText(/Beginn/)).toBeInTheDocument()
    expect(screen.getByText(/Ende/)).toBeInTheDocument()
  })

  it.each([
    ['AVAILABLE', 'Verfügbar'],
    ['RESERVED', 'Reserviert'],
    ['OCCUPIED', 'Belegt'],
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

    expect(screen.getByRole('status', { name: label })).toHaveClass(`room-display-status-${status.toLowerCase()}`)
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

    const status = screen.getByRole('status', { name: 'Verfügbar' })
    const currentDateTime = screen.getByLabelText('Aktuelles Datum und Uhrzeit')

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

    expect(screen.getByText('Nächste Reservierung')).toBeInTheDocument()
    expect(screen.getByText('Reserviert für: Next Team')).toBeInTheDocument()
    const nextReservationLine = screen.getByTestId('room-display-next-reservation-line')
    expect(within(nextReservationLine).getByText('Beginn')).toBeInTheDocument()
    expect(within(nextReservationLine).getByText('Ende')).toBeInTheDocument()
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
    expect(line).toHaveTextContent('Nächste Reservierung')
    expect(line).toHaveTextContent('Reserviert für: Next Team')
    expect(within(line).getByText('Beginn')).toBeInTheDocument()
    expect(within(line).getByText('Ende')).toBeInTheDocument()
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

    expect(screen.getByRole('status', { name: 'Keine weitere Reservierung geplant' })).toBeInTheDocument()
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

    expect(screen.getByText('Reserviert für: Nicht angegeben')).toBeInTheDocument()
  })


  it('shows a scannable check-in QR code when a check-in link is given', async () => {
    render(
      <RoomDisplay
        roomName="Room 101"
        currentDateTime={new Date('2026-09-20T10:00:00')}
        reservation={null}
        state="no-reservation"
        status="AVAILABLE"
        nextReservation={null}
        checkInLink="https://raumlotse.example/rooms/room-1/check-in?method=qr"
      />,
    )

    expect(await screen.findByRole('img', { name: 'QR-Code für den Check-in in Room 101' })).toBeInTheDocument()
    expect(screen.getByText('Zum Einchecken scannen')).toBeInTheDocument()
  })

  it('hides the check-in QR code while the room is unavailable', () => {
    render(
      <RoomDisplay
        roomName="Raum nicht verfügbar"
        currentDateTime={new Date('2026-09-20T10:00:00')}
        reservation={null}
        state="unavailable"
        status="UNAVAILABLE"
        nextReservation={null}
        checkInLink="https://raumlotse.example/rooms/room-1/check-in?method=qr"
      />,
    )

    expect(screen.queryByText('Zum Einchecken scannen')).not.toBeInTheDocument()
  })
})

import { render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import { MyUpcomingReservations } from './MyUpcomingReservations'
import * as reservationsApi from '../../API/reservations'
import type { Reservation } from '../../types/reservation'

vi.mock('../../API/reservations')

const reservations = vi.mocked(reservationsApi)

function sampleReservation(overrides: Partial<Reservation> = {}): Reservation {
  return {
    id: 'res-1',
    roomId: 'room-1',
    roomName: 'Seminarraum 101',
    startTime: '2026-10-01T10:00:00Z',
    endTime: '2026-10-01T11:30:00Z',
    status: 'RESERVED',
    seatingArrangement: { id: 'seat-1', name: 'Theater', maxCapacity: 40 },
    expectedAttendees: 20,
    additionalEquipment: [],
    createdBy: 'user-123',
    reservedFor: 'Projektgruppe Web',
    createdAt: '2026-09-28T10:00:00Z',
    ownedByMe: true,
    ...overrides,
  }
}

describe('MyUpcomingReservations', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('renders loading state initially', () => {
    reservations.getMyUpcomingReservations.mockReturnValue(new Promise(() => {}))
    render(
      <MemoryRouter>
        <MyUpcomingReservations />
      </MemoryRouter>,
    )

    expect(screen.getByText(/reservierungen werden geladen|loading/i)).toBeInTheDocument()
  })

  it('renders empty state when user has no upcoming reservations', async () => {
    reservations.getMyUpcomingReservations.mockResolvedValue([])

    render(
      <MemoryRouter>
        <MyUpcomingReservations />
      </MemoryRouter>,
    )

    await waitFor(() => {
      expect(screen.getByText(/keine anstehenden reservierungen vorhanden/i)).toBeInTheDocument()
    })
    expect(screen.getByRole('heading', { name: /meine nächsten reservierungen|my upcoming reservations/i })).toBeInTheDocument()
  })

  it('renders upcoming reservations table with time, room link, and duration', async () => {
    const mockList: Reservation[] = [
      sampleReservation({
        id: 'res-1',
        roomId: 'room-1',
        roomName: 'Seminarraum 101',
        startTime: '2026-10-01T10:00:00Z',
        endTime: '2026-10-01T11:30:00Z',
      }),
      sampleReservation({
        id: 'res-2',
        roomId: 'room-2',
        roomName: 'Audimax',
        startTime: '2026-10-02T14:00:00Z',
        endTime: '2026-10-02T14:45:00Z',
      }),
    ]

    reservations.getMyUpcomingReservations.mockResolvedValue(mockList)

    render(
      <MemoryRouter>
        <MyUpcomingReservations />
      </MemoryRouter>,
    )

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /meine nächsten reservierungen|my upcoming reservations/i })).toBeInTheDocument()
    })

    const roomLink1 = screen.getByRole('link', { name: 'Seminarraum 101' })
    expect(roomLink1).toBeInTheDocument()
    expect(roomLink1).toHaveAttribute('href', '/rooms/room-1')

    const roomLink2 = screen.getByRole('link', { name: 'Audimax' })
    expect(roomLink2).toBeInTheDocument()
    expect(roomLink2).toHaveAttribute('href', '/rooms/room-2')

    // Durations
    expect(screen.getByText('1 Std. 30 Min.')).toBeInTheDocument()
    expect(screen.getByText('45 Min.')).toBeInTheDocument()
  })

  it('renders error message when API call fails', async () => {
    reservations.getMyUpcomingReservations.mockRejectedValue(new Error('Network error'))

    render(
      <MemoryRouter>
        <MyUpcomingReservations />
      </MemoryRouter>,
    )

    await waitFor(() => {
      expect(screen.getByRole('alert')).toBeInTheDocument()
    })
  })
})

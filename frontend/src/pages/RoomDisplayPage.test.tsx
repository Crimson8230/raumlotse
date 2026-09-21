import { act, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as roomsApi from '../API/rooms'
import * as reservationsApi from '../API/reservations'
import type { Room } from '../types/room'
import RoomDisplayPage from './RoomDisplayPage'

vi.mock('../API/rooms')
vi.mock('../API/reservations')

const rooms = vi.mocked(roomsApi)
const reservations = vi.mocked(reservationsApi)

const sampleRoom: Room = {
  id: 'room-1',
  name: 'Room 101',
  building: { id: 'b1', name: 'Main Building', status: 'ACTIVE' },
  floor: { id: 'f1', buildingId: 'b1', name: '1st Floor', status: 'ACTIVE' },
  status: 'ACTIVE',
  version: 0,
  seatingArrangements: [{ id: 'seat-1', name: 'Theater', maxCapacity: 40 }],
  equipmentTypeIds: [],
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date('2026-09-20T10:00:00.000Z'))
})

afterEach(() => {
  vi.useRealTimers()
})

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/rooms/room-1/display']}>
      <Routes>
        <Route path="/rooms/:roomId/display" element={<RoomDisplayPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('RoomDisplayPage', () => {
  it('loads the room and its reservations for the display', async () => {
    rooms.getRoom.mockResolvedValue(sampleRoom)
    reservations.listRoomReservations.mockResolvedValue([
      {
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
      },
    ])

    renderPage()

    expect(await screen.findByRole('heading', { name: 'Room 101' })).toBeInTheDocument()
    expect(screen.getByText('Team meeting')).toBeInTheDocument()
    expect(screen.getByText('Booked by: Alice')).toBeInTheDocument()
    expect(rooms.getRoom).toHaveBeenCalledWith('room-1')
    expect(reservations.listRoomReservations).toHaveBeenCalledWith('room-1')
  })

  it('shows an unavailable state when room loading fails', async () => {
    rooms.getRoom.mockRejectedValue(new Error('Room load failed'))
    reservations.listRoomReservations.mockResolvedValue([])

    renderPage()

    expect(await screen.findByRole('alert')).toHaveTextContent(/unavailable/i)
    expect(screen.queryByText('Room load failed')).not.toBeInTheDocument()
  })

  it('shows an unavailable state when reservation loading fails', async () => {
    rooms.getRoom.mockResolvedValue(sampleRoom)
    reservations.listRoomReservations.mockRejectedValue(new Error('Reservation load failed'))

    renderPage()

    expect(await screen.findByRole('alert')).toHaveTextContent(/unavailable/i)
    expect(screen.queryByText('Reservation load failed')).not.toBeInTheDocument()
  })

  it('refreshes the displayed clock every 30 seconds', async () => {
    vi.useRealTimers()
    vi.useFakeTimers({ toFake: ['Date', 'setInterval', 'clearInterval'] })
    vi.setSystemTime(new Date('2026-09-20T10:00:00.000Z'))
    rooms.getRoom.mockResolvedValue(sampleRoom)
    reservations.listRoomReservations.mockResolvedValue([])

    renderPage()

    expect(await screen.findByRole('heading', { name: 'Room 101' })).toBeInTheDocument()
    const clock = screen.getByLabelText('Current date and time')
    const initialClock = clock.textContent

    act(() => {
      vi.setSystemTime(new Date('2026-09-20T10:00:30.000Z'))
      vi.advanceTimersByTime(30_000)
    })

    expect(clock.textContent).not.toBe(initialClock)
  })

  it('shows a same-day upcoming reservation when no reservation is currently active', async () => {
    rooms.getRoom.mockResolvedValue(sampleRoom)
    reservations.listRoomReservations.mockResolvedValue([
      {
        id: 'res-upcoming',
        roomId: 'room-1',
        roomName: 'Room 101',
        startTime: '2026-09-20T12:00:00.000Z',
        endTime: '2026-09-20T13:00:00.000Z',
        status: 'RESERVED',
        seatingArrangement: { id: 'seat-1', name: 'Theater', maxCapacity: 40 },
        expectedAttendees: 20,
        additionalEquipment: [],
        note: 'Later meeting',
        createdBy: 'Alice',
        createdAt: '2026-09-19T09:00:00.000Z',
      },
    ])

    renderPage()

    expect(await screen.findByRole('heading', { name: 'Upcoming reservation' })).toBeInTheDocument()
    expect(screen.getByRole('status', { name: 'No current reservation' })).toBeInTheDocument()
  })

  it('does not show tomorrow reservation as upcoming', async () => {
    rooms.getRoom.mockResolvedValue(sampleRoom)
    reservations.listRoomReservations.mockResolvedValue([
      {
        id: 'res-tomorrow',
        roomId: 'room-1',
        roomName: 'Room 101',
        startTime: '2026-09-21T09:00:00.000Z',
        endTime: '2026-09-21T10:00:00.000Z',
        status: 'RESERVED',
        seatingArrangement: { id: 'seat-1', name: 'Theater', maxCapacity: 40 },
        expectedAttendees: 20,
        additionalEquipment: [],
        note: 'Tomorrow meeting',
        createdBy: 'Alice',
        createdAt: '2026-09-19T09:00:00.000Z',
      },
    ])

    renderPage()

    expect(await screen.findByRole('heading', { name: 'Room 101' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Upcoming reservation' })).not.toBeInTheDocument()
  })
})

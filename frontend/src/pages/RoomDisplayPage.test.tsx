import { act, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as roomsApi from '../API/rooms'
import * as reservationsApi from '../API/reservations'
import * as roomStatusApi from '../API/roomStatus'
import * as routingApi from '../API/routing'
import type { Room } from '../types/room'
import RoomDisplayPage from './RoomDisplayPage'

vi.mock('../API/rooms')
vi.mock('../API/reservations')
vi.mock('../API/roomStatus')
vi.mock('../API/routing')
const mode = vi.hoisted(() => ({ adminMode: false }))
vi.mock('../auth/useAdminMode', () => ({
  useAdminMode: () => ({ loading: false, failed: false, admin: mode.adminMode, adminMode: mode.adminMode, setMode: vi.fn() }),
}))

const rooms = vi.mocked(roomsApi)
const reservations = vi.mocked(reservationsApi)
const roomStatus = vi.mocked(roomStatusApi)
const routing = vi.mocked(routingApi)

const sampleRoom: Room = {
  id: 'room-1',
  name: 'Room 101',
  building: { id: 'b1', name: 'Main Building', status: 'ACTIVE', hasElevator: false },
  floor: { id: 'f1', buildingId: 'b1', name: '1st Floor', status: 'ACTIVE', groundFloor: false, position: 0 },
  status: 'ACTIVE',
  version: 0,
  seatingArrangements: [{ id: 'seat-1', name: 'Theater', maxCapacity: 40 }],
  equipmentTypeIds: [],
  notBarrierFree: false,
  barrierFreeReachable: false,
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date('2026-09-20T10:00:00.000Z'))
  mode.adminMode = false
  routing.getRoomNeighbors.mockResolvedValue({ roomId: 'room-1', available: false, reason: 'ROOM_NOT_ATTACHED', entries: [] })
  roomStatus.getRoomStatus.mockResolvedValue({
    roomId: 'room-1', status: 'AVAILABLE', devices: { lighting: false, ventilation: false, door: 'LOCKED' }, lastPresenceAt: null,
  })
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
        reservedFor: 'Team Alpha',
        createdAt: '2026-09-19T09:00:00.000Z',
        ownedByMe: true,
      },
    ])

    renderPage()

    expect(await screen.findByRole('heading', { name: 'Room 101' })).toBeInTheDocument()
    expect(screen.getByText('Team meeting')).toBeInTheDocument()
    expect(screen.getByText('Gebucht von: Alice')).toBeInTheDocument()
    expect(rooms.getRoom).toHaveBeenCalledWith('room-1')
    expect(reservations.listRoomReservations).toHaveBeenCalledWith('room-1')
  })

  it('shows an unavailable state when room loading fails', async () => {
    rooms.getRoom.mockRejectedValue(new Error('Room load failed'))
    reservations.listRoomReservations.mockResolvedValue([])

    renderPage()

    expect(await screen.findByRole('alert')).toHaveTextContent(/nicht verfügbar/i)
    expect(screen.queryByText('Room load failed')).not.toBeInTheDocument()
  })

  it('shows an unavailable state when reservation loading fails', async () => {
    rooms.getRoom.mockResolvedValue(sampleRoom)
    reservations.listRoomReservations.mockRejectedValue(new Error('Reservation load failed'))

    renderPage()

    expect(await screen.findByRole('alert')).toHaveTextContent(/nicht verfügbar/i)
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
    const clock = screen.getByLabelText('Aktuelles Datum und Uhrzeit')
    const initialClock = clock.textContent

    act(() => {
      vi.setSystemTime(new Date('2026-09-20T10:00:30.000Z'))
      vi.advanceTimersByTime(30_000)
    })

    expect(clock.textContent).not.toBe(initialClock)
  })

  it('refreshes room reservations every 30 seconds and reflects an active transition', async () => {
    vi.useRealTimers()
    vi.useFakeTimers({ toFake: ['Date', 'setInterval', 'clearInterval'] })
    vi.setSystemTime(new Date('2026-09-20T10:00:00.000Z'))
    rooms.getRoom.mockResolvedValue(sampleRoom)
    reservations.listRoomReservations
      .mockResolvedValueOnce([
        {
          id: 'res-1',
          roomId: 'room-1',
          roomName: 'Room 101',
          startTime: '2026-09-20T10:01:00.000Z',
          endTime: '2026-09-20T11:30:00.000Z',
          status: 'RESERVED',
          seatingArrangement: { id: 'seat-1', name: 'Theater', maxCapacity: 40 },
          expectedAttendees: 20,
          additionalEquipment: [],
          note: null,
          createdBy: 'Alice',
          reservedFor: 'Team Alpha',
          createdAt: '2026-09-19T09:00:00.000Z',
          ownedByMe: true,
        },
      ])
      .mockResolvedValueOnce([
        {
          id: 'res-1',
          roomId: 'room-1',
          roomName: 'Room 101',
          startTime: '2026-09-20T10:01:00.000Z',
          endTime: '2026-09-20T11:30:00.000Z',
          status: 'ACTIVE',
          seatingArrangement: { id: 'seat-1', name: 'Theater', maxCapacity: 40 },
          expectedAttendees: 20,
          additionalEquipment: [],
          note: null,
          createdBy: 'Alice',
          reservedFor: 'Team Alpha',
          createdAt: '2026-09-19T09:00:00.000Z',
          ownedByMe: true,
        },
      ])

    renderPage()

    expect(await screen.findByRole('status', { name: 'Verfügbar' })).toBeInTheDocument()

    await act(async () => {
      vi.setSystemTime(new Date('2026-09-20T10:01:00.000Z'))
      vi.advanceTimersByTime(30_000)
      await Promise.resolve()
      await Promise.resolve()
    })

    expect(reservations.listRoomReservations).toHaveBeenCalledTimes(2)
    expect(await screen.findByRole('status', { name: 'Belegt' })).toBeInTheDocument()
  })


  it('shows the QR code for checking in to this room', async () => {
    rooms.getRoom.mockResolvedValue(sampleRoom)
    reservations.listRoomReservations.mockResolvedValue([])

    renderPage()

    expect(await screen.findByRole('img', { name: 'QR-Code für den Check-in in Room 101' })).toBeInTheDocument()
  })

  it('shows the device states and refreshes them with the display', async () => {
    vi.useFakeTimers({ toFake: ['Date', 'setInterval', 'clearInterval'] })
    vi.setSystemTime(new Date('2026-09-20T10:00:00.000Z'))
    rooms.getRoom.mockResolvedValue(sampleRoom)
    reservations.listRoomReservations.mockResolvedValue([])

    renderPage()

    await act(async () => { await Promise.resolve() })
    expect(await screen.findByText('Licht aus')).toBeInTheDocument()
    expect(screen.getByText('Tür verriegelt')).toBeInTheDocument()
    expect(roomStatus.getRoomStatus).toHaveBeenCalledWith('room-1')

    roomStatus.getRoomStatus.mockResolvedValue({
      roomId: 'room-1', status: 'OCCUPIED', devices: { lighting: true, ventilation: true, door: 'UNLOCKED' }, lastPresenceAt: null,
    })
    await act(async () => {
      vi.advanceTimersByTime(30_000)
      await Promise.resolve()
    })

    expect(await screen.findByText('Licht an')).toBeInTheDocument()
    expect(screen.getByText('Tür entriegelt')).toBeInTheDocument()
    expect(roomStatus.getRoomStatus).toHaveBeenCalledTimes(2)
  })

  it('offers the motion simulation only in administration mode', async () => {
    rooms.getRoom.mockResolvedValue(sampleRoom)
    reservations.listRoomReservations.mockResolvedValue([])
    const { unmount } = renderPage()
    expect(await screen.findByText('Licht aus')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Bewegung simulieren' })).not.toBeInTheDocument()
    unmount()

    mode.adminMode = true
    renderPage()
    expect(await screen.findByRole('button', { name: 'Bewegung simulieren' })).toBeInTheDocument()
  })

  it('keeps showing the room when the device status cannot be loaded', async () => {
    rooms.getRoom.mockResolvedValue(sampleRoom)
    reservations.listRoomReservations.mockResolvedValue([])
    roomStatus.getRoomStatus.mockRejectedValue(new Error('status unavailable'))

    renderPage()

    expect(await screen.findByRole('heading', { name: 'Room 101' })).toBeInTheDocument()
    expect(screen.getByText('Keine aktuelle Reservierung')).toBeInTheDocument()
    expect(screen.queryByRole('list', { name: 'Geräte im Raum' })).not.toBeInTheDocument()
  })

  it('links back to the room page', async () => {
    rooms.getRoom.mockResolvedValue(sampleRoom)
    reservations.listRoomReservations.mockResolvedValue([])

    renderPage()

    const back = await screen.findByRole('link', { name: 'Zurück zum Raum' })
    expect(back.closest('main')).toHaveClass('room-display')
    expect(back).toHaveAttribute('href', '/rooms/room-1')
    expect(back.parentElement).toHaveClass('page-back')
  })
})

describe('RoomDisplayPage neighbors (feature 024)', () => {
  const neighbors = {
    roomId: 'room-1',
    available: true,
    reason: null,
    entries: [
      {
        direction: 'LEFT' as const,
        kind: 'ROOM' as const,
        roomId: 'room-2',
        connectionId: null,
        name: 'Room 102',
        targetFloorName: null,
        distanceMeters: 7,
        walkingSeconds: 6,
        status: 'OCCUPIED' as const,
      },
    ],
  }

  it('shows the neighbors next to the room data', async () => {
    rooms.getRoom.mockResolvedValue(sampleRoom)
    reservations.listRoomReservations.mockResolvedValue([])
    routing.getRoomNeighbors.mockResolvedValue(neighbors)

    renderPage()

    const section = await screen.findByRole('region', { name: 'Nachbarräume' })
    expect(section).toHaveTextContent('Room 102 · links · ca. 7 m (6 s)')
    expect(routing.getRoomNeighbors).toHaveBeenCalledWith('room-1')
  })

  it('still renders the display when the neighbors request fails', async () => {
    rooms.getRoom.mockResolvedValue(sampleRoom)
    reservations.listRoomReservations.mockResolvedValue([])
    routing.getRoomNeighbors.mockRejectedValue(new Error('boom'))

    renderPage()

    expect(await screen.findByRole('heading', { name: 'Room 101' })).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Nachbarräume' })).not.toBeInTheDocument()
  })

  it('omits the neighbor area when none can be shown', async () => {
    rooms.getRoom.mockResolvedValue(sampleRoom)
    reservations.listRoomReservations.mockResolvedValue([])

    renderPage()

    expect(await screen.findByRole('heading', { name: 'Room 101' })).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Nachbarräume' })).not.toBeInTheDocument()
  })
})

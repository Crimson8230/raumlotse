import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import RoomDetailPage from './RoomDetailPage'
import * as roomsApi from '../API/rooms'
import * as reservationsApi from '../API/reservations'
import type { Room } from '../types/room'

vi.mock('../API/rooms')
vi.mock('../API/reservations')

vi.mock('../auth/useAuth', () => ({
  useAuth: () => ({
    state: 'authenticated',
    user: { userId: 'user-1', displayName: 'Jane Doe' },
  }),
}))

const rooms = vi.mocked(roomsApi)
const reservations = vi.mocked(reservationsApi)
const SEATING_ID = '00000000-0000-4000-8000-000000000001'

function sampleRoom(): Room {
  return {
    id: 'room-1',
    name: 'Room 101',
    building: { id: 'b1', name: 'Main Building', status: 'ACTIVE', hasElevator: false },
    floor: { id: 'f1', buildingId: 'b1', name: '1st Floor', status: 'ACTIVE', groundFloor: false },
    status: 'ACTIVE',
    version: 0,
    seatingArrangements: [{ id: SEATING_ID, name: 'Theater', maxCapacity: 40 }],
    equipmentTypeIds: [],
    notBarrierFree: false,
    barrierFreeReachable: false,
  }
}

function renderComponent(url = '/rooms/room-1') {
  return render(
    <MemoryRouter initialEntries={[url]}>
      <Routes>
        <Route path="/rooms/:roomId" element={<RoomDetailPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  vi.resetAllMocks()
  reservations.getAvailableEquipment.mockResolvedValue([])
  reservations.listRoomReservations.mockResolvedValue([])
})

describe('RoomDetailPage', () => {
  it('renders room details and toggles reservation booking form', async () => {
    const user = userEvent.setup()
    rooms.getRoom.mockResolvedValue(sampleRoom())

    renderComponent()

    expect(await screen.findByText('Room 101')).toBeInTheDocument()
    expect(screen.getByText('Main Building')).toBeInTheDocument()
    expect(screen.getByText('1st Floor')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Display Room Information' })).toHaveAttribute(
      'href',
      '/rooms/room-1/display',
    )

    const bookBtn = screen.getByRole('button', { name: /book room/i })
    await user.click(bookBtn)

    expect(screen.getByLabelText(/start time/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /close booking form/i })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /close booking form/i }))
    expect(screen.queryByLabelText(/start time/i)).not.toBeInTheDocument()
  })

  it('renders reservations list in room detail page', async () => {
    rooms.getRoom.mockResolvedValue(sampleRoom())
    reservations.listRoomReservations.mockResolvedValue([
      {
        id: 'res-1',
        roomId: 'room-1',
        roomName: 'Room 101',
        startTime: '2026-10-01T10:00:00Z',
        endTime: '2026-10-01T11:00:00Z',
        status: 'RESERVED',
        seatingArrangement: { id: SEATING_ID, name: 'Theater', maxCapacity: 40 },
        expectedAttendees: 25,
        additionalEquipment: [],
        note: null,
        createdBy: 'Alice Bob',
        reservedFor: 'Alice Bob',
        createdAt: '2026-09-19T09:00:00Z',
      },
    ])

    renderComponent()

    expect((await screen.findAllByText(/Alice Bob/)).length).toBeGreaterThanOrEqual(1)
    expect(screen.getByText('RESERVED')).toBeInTheDocument()
  })

  it('shows whether the room is barrier-free reachable', async () => {
    rooms.getRoom.mockResolvedValue({ ...sampleRoom(), barrierFreeReachable: true })

    renderComponent()

    expect(await screen.findByText('Barrierefrei erreichbar')).toBeInTheDocument()
    expect(screen.getByText('Ja')).toBeInTheDocument()
  })

  it('shows "Nein" for a room that is not barrier-free reachable', async () => {
    rooms.getRoom.mockResolvedValue(sampleRoom())

    renderComponent()

    expect(await screen.findByText('Barrierefrei erreichbar')).toBeInTheDocument()
    expect(screen.getByText('Nein')).toBeInTheDocument()
  })

  const start = new Date(2026, 9, 5, 11, 0)
  const end = new Date(2026, 9, 5, 12, 0)
  const prefillUrl = `/rooms/room-1?start=${encodeURIComponent(start.toISOString())}&end=${encodeURIComponent(end.toISOString())}`

  it('opens the booking form pre-filled when the URL carries a search window', async () => {
    rooms.getRoom.mockResolvedValue(sampleRoom())

    renderComponent(prefillUrl)

    expect(await screen.findByLabelText(/start time/i)).toHaveValue('2026-10-05T11:00')
    expect(screen.getByLabelText(/end time/i)).toHaveValue('2026-10-05T12:00')
  })

  it.each([
    ['partial parameters', `/rooms/room-1?start=${encodeURIComponent(start.toISOString())}`],
    ['invalid dates', '/rooms/room-1?start=nope&end=also-nope'],
    ['an end before the start', `/rooms/room-1?start=${encodeURIComponent(end.toISOString())}&end=${encodeURIComponent(start.toISOString())}`],
  ])('keeps the booking form closed for %s', async (_case, url) => {
    rooms.getRoom.mockResolvedValue(sampleRoom())

    renderComponent(url)

    expect(await screen.findByRole('button', { name: /book room/i })).toBeInTheDocument()
    expect(screen.queryByLabelText(/start time/i)).not.toBeInTheDocument()
  })

  it('never opens the booking form for a deactivated room', async () => {
    rooms.getRoom.mockResolvedValue({ ...sampleRoom(), status: 'DEACTIVATED' })

    renderComponent(prefillUrl)

    expect(await screen.findByText('Room 101')).toBeInTheDocument()
    expect(screen.queryByLabelText(/start time/i)).not.toBeInTheDocument()
  })

  it('submits the pre-filled, possibly edited window', async () => {
    const user = userEvent.setup()
    rooms.getRoom.mockResolvedValue(sampleRoom())
    reservations.createReservation.mockResolvedValue({
      id: 'res-9',
      roomId: 'room-1',
      roomName: 'Room 101',
      startTime: start.toISOString(),
      endTime: end.toISOString(),
      status: 'RESERVED',
      seatingArrangement: { id: SEATING_ID, name: 'Theater', maxCapacity: 40 },
      expectedAttendees: 10,
      additionalEquipment: [],
      note: null,
      createdBy: 'Alice',
      reservedFor: 'Jane Doe',
      createdAt: start.toISOString(),
    })

    renderComponent(prefillUrl)
    const endInput = await screen.findByLabelText(/end time/i)
    await user.clear(endInput)
    await user.type(endInput, '2026-10-05T12:30')
    await user.type(screen.getByLabelText(/attendees/i), '10')
    await user.click(screen.getByRole('button', { name: /confirm reservation/i }))

    expect(reservations.createReservation).toHaveBeenCalledWith(
      'room-1',
      expect.objectContaining({
        startTime: start.toISOString(),
        endTime: new Date(2026, 9, 5, 12, 30).toISOString(),
      }),
    )
  })

  it('opens an empty booking form for ?book=true', async () => {
    rooms.getRoom.mockResolvedValue(sampleRoom())

    renderComponent('/rooms/room-1?book=true')

    expect(await screen.findByLabelText(/start time/i)).toHaveValue('')
    expect(screen.getByLabelText(/end time/i)).toHaveValue('')
  })

  it('opens the booking form pre-filled for ?book=true with a window', async () => {
    rooms.getRoom.mockResolvedValue(sampleRoom())

    renderComponent(`${prefillUrl}&book=true`)

    expect(await screen.findByLabelText(/start time/i)).toHaveValue('2026-10-05T11:00')
  })

  it('never opens the booking form via ?book=true for a deactivated room', async () => {
    rooms.getRoom.mockResolvedValue({ ...sampleRoom(), status: 'DEACTIVATED' })

    renderComponent('/rooms/room-1?book=true')

    expect(await screen.findByText('Room 101')).toBeInTheDocument()
    expect(screen.queryByLabelText(/start time/i)).not.toBeInTheDocument()
  })
})

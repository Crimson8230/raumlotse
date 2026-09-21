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

const rooms = vi.mocked(roomsApi)
const reservations = vi.mocked(reservationsApi)

function sampleRoom(): Room {
  return {
    id: 'room-1',
    name: 'Room 101',
    building: { id: 'b1', name: 'Main Building', status: 'ACTIVE' },
    floor: { id: 'f1', buildingId: 'b1', name: '1st Floor', status: 'ACTIVE' },
    status: 'ACTIVE',
    version: 0,
    seatingArrangements: [{ id: 'seat-1', name: 'Theater', maxCapacity: 40 }],
    equipmentTypeIds: [],
  }
}

function renderComponent() {
  return render(
    <MemoryRouter initialEntries={['/rooms/room-1']}>
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
        seatingArrangement: { id: 'seat-1', name: 'Theater', maxCapacity: 40 },
        expectedAttendees: 25,
        additionalEquipment: [],
        note: null,
        createdBy: 'Alice Bob',
        createdAt: '2026-09-19T09:00:00Z',
      },
    ])

    renderComponent()

    expect(await screen.findByText(/Alice Bob/)).toBeInTheDocument()
    expect(screen.getByText('RESERVED')).toBeInTheDocument()
  })
})

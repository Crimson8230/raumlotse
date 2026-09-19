import { MemoryRouter } from 'react-router-dom'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import RoomListPage from './RoomListPage'
import * as roomsApi from '../API/rooms'
import type { Room } from '../types/room'

vi.mock('../API/rooms')

const rooms = vi.mocked(roomsApi)

function room(overrides: Partial<Room> = {}): Room {
  return {
    id: 'r1',
    name: 'Room 101',
    building: { id: 'b1', name: 'Main', status: 'ACTIVE' },
    floor: { id: 'f1', buildingId: 'b1', name: '1', status: 'ACTIVE' },
    status: 'ACTIVE',
    version: 0,
    seatingArrangements: [{ id: 's1', name: 'Theater', maxCapacity: 40 }],
    equipmentTypeIds: [],
    ...overrides,
  }
}

function renderPage() {
  return render(
    <MemoryRouter>
      <RoomListPage />
    </MemoryRouter>,
  )
}

beforeEach(() => {
  vi.resetAllMocks()
})

describe('RoomListPage', () => {
  it('renders rooms with name, building, floor, and status', async () => {
    rooms.listRooms.mockResolvedValue([room()])

    renderPage()

    await screen.findByText('Room 101')
    expect(screen.getByText('Main')).toBeInTheDocument()
    expect(screen.getByText('1')).toBeInTheDocument()
    expect(screen.getByText('ACTIVE')).toBeInTheDocument()
  })

  it('renders the room status with a semantic status class', async () => {
    rooms.listRooms.mockResolvedValue([room(), room({ id: 'r2', name: 'Room 102', status: 'DEACTIVATED' })])

    renderPage()

    await screen.findByText('Room 101')
    expect(screen.getByText('ACTIVE')).toHaveClass('status-active')
    expect(screen.getByText('DEACTIVATED')).toHaveClass('status-deactivated')
  })

  it('renders a styled empty state instead of a blank list when there are no rooms', async () => {
    rooms.listRooms.mockResolvedValue([])

    renderPage()

    expect(await screen.findByText(/no rooms match/i)).toHaveClass('status-empty')
    expect(screen.queryByRole('list')).not.toBeInTheDocument()
  })

  it('reloads with the deactivated filter when selected', async () => {
    const user = userEvent.setup()
    rooms.listRooms.mockResolvedValue([])

    renderPage()
    await waitFor(() => expect(rooms.listRooms).toHaveBeenCalledWith('active'))

    await user.selectOptions(screen.getByLabelText(/status/i), 'deactivated')

    await waitFor(() => expect(rooms.listRooms).toHaveBeenCalledWith('deactivated'))
  })

  it('deactivates a room, hiding it from the active list on reload', async () => {
    const user = userEvent.setup()
    rooms.listRooms.mockResolvedValueOnce([room()]).mockResolvedValueOnce([])
    rooms.deactivateRoom.mockResolvedValue(room({ status: 'DEACTIVATED' }))

    renderPage()
    await screen.findByText('Room 101')

    await user.click(screen.getByRole('button', { name: /deactivate room 101/i }))

    await waitFor(() => expect(rooms.deactivateRoom).toHaveBeenCalledWith('r1'))
    await waitFor(() => expect(rooms.listRooms).toHaveBeenCalledTimes(2))
  })

  it('reactivates a deactivated room', async () => {
    const user = userEvent.setup()
    rooms.listRooms.mockResolvedValue([room({ status: 'DEACTIVATED' })])
    rooms.reactivateRoom.mockResolvedValue(room())

    renderPage()
    await screen.findByText('Room 101')

    await user.click(screen.getByRole('button', { name: /reactivate room 101/i }))

    await waitFor(() => expect(rooms.reactivateRoom).toHaveBeenCalledWith('r1'))
  })

  it('deletes a room, removing it from the list', async () => {
    const user = userEvent.setup()
    rooms.listRooms.mockResolvedValueOnce([room()]).mockResolvedValueOnce([])
    rooms.deleteRoom.mockResolvedValue(undefined)

    renderPage()
    await screen.findByText('Room 101')

    await user.click(screen.getByRole('button', { name: /delete room 101/i }))

    await waitFor(() => expect(rooms.deleteRoom).toHaveBeenCalledWith('r1'))
    await waitFor(() => expect(rooms.listRooms).toHaveBeenCalledTimes(2))
  })

  it('displays error when deactivating a room blocked by active reservations', async () => {
    const user = userEvent.setup()
    rooms.listRooms.mockResolvedValue([room()])
    rooms.deactivateRoom.mockRejectedValue(
      new Error('Room has active or upcoming reservations; cancel them first.'),
    )

    renderPage()
    await screen.findByText('Room 101')

    await user.click(screen.getByRole('button', { name: /deactivate room 101/i }))

    expect(
      await screen.findByText(/Room has active or upcoming reservations/i),
    ).toBeInTheDocument()
  })
})

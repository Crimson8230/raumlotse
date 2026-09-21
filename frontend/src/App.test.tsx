import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import * as roomsApi from './API/rooms'
import * as reservationsApi from './API/reservations'
import type { Room } from './types/room'

vi.mock('./API/rooms')
vi.mock('./API/reservations')

const rooms = vi.mocked(roomsApi)
const reservations = vi.mocked(reservationsApi)

const sampleRoom: Room = {
  id: 'room-1',
  name: 'Room 101',
  building: { id: 'b1', name: 'Main Building', status: 'ACTIVE' },
  floor: { id: 'f1', buildingId: 'b1', name: '1st Floor', status: 'ACTIVE' },
  status: 'ACTIVE',
  version: 0,
  seatingArrangements: [],
  equipmentTypeIds: [],
}

beforeEach(() => {
  vi.resetAllMocks()
  rooms.getRoom.mockResolvedValue(sampleRoom)
  reservations.listRoomReservations.mockResolvedValue([])
  reservations.getAvailableEquipment.mockResolvedValue([])
})

describe('App display routing', () => {
  it('renders the room display route without changing the existing app shell', async () => {
    render(
      <MemoryRouter initialEntries={['/rooms/room-1/display']}>
        <App />
      </MemoryRouter>,
    )

    expect(await screen.findByRole('heading', { name: 'Room 101' })).toBeInTheDocument()
    expect(screen.getByText('No current reservation')).toBeInTheDocument()
  })

  it('preserves the existing room detail route', async () => {
    render(
      <MemoryRouter initialEntries={['/rooms/room-1']}>
        <App />
      </MemoryRouter>,
    )

    expect(await screen.findByRole('heading', { name: 'Room 101' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Reservations' })).toBeInTheDocument()
  })
})

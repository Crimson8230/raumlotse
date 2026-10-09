import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import * as roomsApi from './API/rooms'
import * as reservationsApi from './API/reservations'
import * as roleApi from './API/userRoles'
import type { Room } from './types/room'

vi.mock('./API/rooms')
vi.mock('./API/reservations')
vi.mock('./API/userRoles')
const auth = vi.hoisted(() => ({ me: vi.fn(), login: vi.fn(), refreshCsrfToken: vi.fn() }))
vi.mock('./API/auth', () => ({ authApi: auth }))
vi.mock('./API/health', () => ({ getHealth: vi.fn().mockResolvedValue({ status: 'ok' }) }))

const rooms = vi.mocked(roomsApi)
const reservations = vi.mocked(reservationsApi)

const sampleRoom: Room = {
  id: 'room-1',
  name: 'Room 101',
  building: { id: 'b1', name: 'Main Building', status: 'ACTIVE', hasElevator: false },
  floor: { id: 'f1', buildingId: 'b1', name: '1st Floor', status: 'ACTIVE', groundFloor: false },
  status: 'ACTIVE',
  version: 0,
  seatingArrangements: [],
  equipmentTypeIds: [],
  notBarrierFree: false,
  barrierFreeReachable: false,
}

beforeEach(() => {
  vi.resetAllMocks()
  auth.me.mockResolvedValue({ userId: 'admin', displayName: 'Administration' })
  auth.refreshCsrfToken.mockResolvedValue(undefined)
  vi.mocked(roleApi.getCurrentRoles).mockResolvedValue({ roles: ['VIEWER'], ready: true, adminMode: false,
    permissions: ['READ'], canUseAdminMode: false })
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
    expect(screen.getByText('Keine aktuelle Reservierung')).toBeInTheDocument()
  })

  it('preserves the existing room detail route', async () => {
    render(
      <MemoryRouter initialEntries={['/rooms/room-1']}>
        <App />
      </MemoryRouter>,
    )

    expect(await screen.findByRole('heading', { name: 'Room 101' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Reservierungen' })).toBeInTheDocument()
  })
})

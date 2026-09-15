import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { RoomForm } from './RoomForm'
import * as buildingsApi from '../../API/buildings'
import * as floorsApi from '../../API/floors'
import * as equipmentApi from '../../API/equipmentTypes'
import * as roomsApi from '../../API/rooms'
import { ApiError } from '../../API/client'
import type { Building, EquipmentType, Floor, Room } from '../../types/room'

vi.mock('../../API/buildings')
vi.mock('../../API/floors')
vi.mock('../../API/equipmentTypes')
vi.mock('../../API/rooms')

const buildings = vi.mocked(buildingsApi)
const floors = vi.mocked(floorsApi)
const equipment = vi.mocked(equipmentApi)
const rooms = vi.mocked(roomsApi)

function building(overrides: Partial<Building> = {}): Building {
  return { id: 'b1', name: 'Main', status: 'ACTIVE', ...overrides }
}

function floor(overrides: Partial<Floor> = {}): Floor {
  return { id: 'f1', buildingId: 'b1', name: '1', status: 'ACTIVE', ...overrides }
}

function equipmentType(overrides: Partial<EquipmentType> = {}): EquipmentType {
  return { id: 'e1', name: 'Projector', status: 'ACTIVE', ...overrides }
}

function room(overrides: Partial<Room> = {}): Room {
  return {
    id: 'r1',
    name: 'Room 101',
    building: building(),
    floor: floor(),
    status: 'ACTIVE',
    version: 0,
    seatingArrangements: [{ id: 's1', name: 'Theater', maxCapacity: 40 }],
    equipmentTypeIds: [],
    ...overrides,
  }
}

beforeEach(() => {
  vi.resetAllMocks()
  buildings.listBuildings.mockResolvedValue([building()])
  floors.listFloors.mockResolvedValue([floor()])
  equipment.listEquipmentTypes.mockResolvedValue([equipmentType()])
})

describe('RoomForm (create mode)', () => {
  it('submits a room with a seating arrangement and selected equipment', async () => {
    const user = userEvent.setup()
    rooms.createRoom.mockResolvedValue(room())
    const onSaved = vi.fn()

    render(<RoomForm onSaved={onSaved} />)

    await user.type(screen.getByLabelText(/room name/i), 'Room 101')
    await user.selectOptions(await screen.findByLabelText(/^building/i), 'b1')
    await user.selectOptions(await screen.findByLabelText(/^floor/i), 'f1')
    await user.type(screen.getByLabelText(/seating arrangement name/i), 'Theater')
    await user.type(screen.getByLabelText(/max capacity/i), '40')
    await user.click(await screen.findByLabelText('Projector'))
    await user.click(screen.getByRole('button', { name: /create room/i }))

    await waitFor(() =>
      expect(rooms.createRoom).toHaveBeenCalledWith({
        name: 'Room 101',
        floorId: 'f1',
        seatingArrangements: [{ name: 'Theater', maxCapacity: 40 }],
        equipmentTypeIds: ['e1'],
      }),
    )
    await waitFor(() => expect(onSaved).toHaveBeenCalledWith(room()))
  })

  it('shows a validation error and does not submit when there is no seating arrangement', async () => {
    const user = userEvent.setup()
    render(<RoomForm onSaved={vi.fn()} />)

    await user.type(screen.getByLabelText(/room name/i), 'Room 101')
    await user.selectOptions(await screen.findByLabelText(/^building/i), 'b1')
    await user.selectOptions(await screen.findByLabelText(/^floor/i), 'f1')
    await user.click(screen.getByRole('button', { name: /remove seating arrangement/i }))
    await user.click(screen.getByRole('button', { name: /create room/i }))

    await screen.findByText(/at least one seating arrangement/i)
    expect(rooms.createRoom).not.toHaveBeenCalled()
    expect(screen.getByText(/at least one seating arrangement/i)).toHaveClass('feedback-error')
  })

  it('lets the administrator create a building and floor inline when none exist yet', async () => {
    const user = userEvent.setup()
    buildings.listBuildings.mockResolvedValue([])
    buildings.createBuilding.mockResolvedValue(building())
    floors.listFloors.mockResolvedValueOnce([]).mockResolvedValue([floor()])
    floors.createFloor.mockResolvedValue(floor())

    render(<RoomForm onSaved={vi.fn()} />)

    await user.click(await screen.findByRole('button', { name: /add new building/i }))
    await user.type(screen.getByLabelText(/new building name/i), 'Main')
    await user.click(screen.getByRole('button', { name: /create building/i }))

    await waitFor(() => expect(buildings.createBuilding).toHaveBeenCalledWith('Main'))

    await user.click(await screen.findByRole('button', { name: /add new floor/i }))
    await user.type(screen.getByLabelText(/new floor name/i), '1')
    await user.click(screen.getByRole('button', { name: /create floor/i }))

    await waitFor(() => expect(floors.createFloor).toHaveBeenCalledWith('b1', '1'))
  })
})

describe('RoomForm (edit mode)', () => {
  it('pre-fills from the loaded room and submits an update with its version', async () => {
    const user = userEvent.setup()
    const existing = room()
    rooms.updateRoom.mockResolvedValue(existing)
    const onSaved = vi.fn()

    render(<RoomForm room={existing} onSaved={onSaved} />)

    expect(await screen.findByDisplayValue('Room 101')).toBeInTheDocument()

    await user.clear(screen.getByLabelText(/room name/i))
    await user.type(screen.getByLabelText(/room name/i), 'Room 102')
    await user.click(screen.getByRole('button', { name: /save room/i }))

    await waitFor(() =>
      expect(rooms.updateRoom).toHaveBeenCalledWith('r1', {
        name: 'Room 102',
        floorId: 'f1',
        seatingArrangements: [{ name: 'Theater', maxCapacity: 40 }],
        equipmentTypeIds: [],
        version: 0,
      }),
    )
    await waitFor(() => expect(onSaved).toHaveBeenCalledWith(existing))
  })

  it('shows a conflict message when the save is based on stale data', async () => {
    const user = userEvent.setup()
    const existing = room()
    rooms.updateRoom.mockRejectedValue(
      new ApiError(409, {
        title: 'Conflict',
        status: 409,
        detail: 'The resource was modified by someone else since it was loaded. Reload and try again.',
      }),
    )

    render(<RoomForm room={existing} onSaved={vi.fn()} />)
    await screen.findByDisplayValue('Room 101')

    await user.click(screen.getByRole('button', { name: /save room/i }))

    const conflictMessage = await screen.findByText(/modified by someone else/i)
    expect(conflictMessage).toHaveClass('feedback-conflict')
    expect(conflictMessage).not.toHaveClass('feedback-error')
  })
})

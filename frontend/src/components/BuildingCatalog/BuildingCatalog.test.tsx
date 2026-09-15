import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { BuildingCatalog } from './BuildingCatalog'
import * as buildingsApi from '../../API/buildings'
import * as floorsApi from '../../API/floors'
import type { Building, Floor } from '../../types/room'

vi.mock('../../API/buildings')
vi.mock('../../API/floors')

const buildings = vi.mocked(buildingsApi)
const floors = vi.mocked(floorsApi)

function building(overrides: Partial<Building> = {}): Building {
  return { id: 'b1', name: 'Main', status: 'ACTIVE', ...overrides }
}

function floor(overrides: Partial<Floor> = {}): Floor {
  return { id: 'f1', buildingId: 'b1', name: '1', status: 'ACTIVE', ...overrides }
}

beforeEach(() => {
  vi.resetAllMocks()
  buildings.listBuildings.mockResolvedValue([])
  floors.listFloors.mockResolvedValue([])
})

describe('BuildingCatalog', () => {
  it('creates a building and then a floor under it', async () => {
    const user = userEvent.setup()
    buildings.listBuildings.mockResolvedValueOnce([]).mockResolvedValue([building()])
    buildings.createBuilding.mockResolvedValue(building())
    floors.listFloors.mockResolvedValue([])
    floors.createFloor.mockResolvedValue(floor())

    render(<BuildingCatalog />)

    await user.type(screen.getByLabelText(/new building name/i), 'Main')
    await user.click(screen.getByRole('button', { name: /add building/i }))

    await waitFor(() => expect(buildings.createBuilding).toHaveBeenCalledWith('Main'))
    await screen.findByText('Main')

    await user.type(screen.getByLabelText(/new floor name/i), '1')
    await user.click(screen.getByRole('button', { name: /add floor/i }))

    await waitFor(() => expect(floors.createFloor).toHaveBeenCalledWith('b1', '1'))
  })

  it('renames a building and a floor', async () => {
    const user = userEvent.setup()
    buildings.listBuildings.mockResolvedValue([building()])
    floors.listFloors.mockResolvedValue([floor()])
    buildings.renameBuilding.mockResolvedValue(building({ name: 'Main Building' }))
    floors.renameFloor.mockResolvedValue(floor({ name: 'Ground' }))

    render(<BuildingCatalog />)
    await screen.findByText('Main')

    await user.clear(screen.getByLabelText(/rename building main/i))
    await user.type(screen.getByLabelText(/rename building main/i), 'Main Building')
    await user.click(screen.getByRole('button', { name: /save building name/i }))
    await waitFor(() => expect(buildings.renameBuilding).toHaveBeenCalledWith('b1', 'Main Building'))
  })

  it('deactivating a building shows its floor as deactivated', async () => {
    const user = userEvent.setup()
    buildings.listBuildings
      .mockResolvedValueOnce([building()])
      .mockResolvedValue([building({ status: 'DEACTIVATED' })])
    floors.listFloors.mockResolvedValue([floor({ status: 'DEACTIVATED' })])
    buildings.deactivateBuilding.mockResolvedValue(building({ status: 'DEACTIVATED' }))

    render(<BuildingCatalog />)
    await screen.findByText('Main')

    await user.click(screen.getByRole('button', { name: /deactivate building main/i }))

    await waitFor(() => expect(buildings.deactivateBuilding).toHaveBeenCalledWith('b1'))
    expect(await screen.findAllByText(/deactivated/i)).not.toHaveLength(0)
  })

  it('shows an error and keeps the building when delete is blocked because it still has floors', async () => {
    const user = userEvent.setup()
    buildings.listBuildings.mockResolvedValue([building()])
    floors.listFloors.mockResolvedValue([floor()])
    buildings.deleteBuilding.mockRejectedValue(
      new (await import('../../API/client')).ApiError(409, {
        title: 'Conflict',
        status: 409,
        detail: "Building 'Main' still has one or more floors; remove them first.",
      }),
    )

    render(<BuildingCatalog />)
    await screen.findByText('Main')

    await user.click(screen.getByRole('button', { name: /delete building main/i }))

    await screen.findByText(/still has one or more floors/i)
    expect(screen.getByText('Main')).toBeInTheDocument()
  })
})

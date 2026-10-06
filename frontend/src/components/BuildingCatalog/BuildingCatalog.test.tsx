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
  return { id: 'b1', name: 'Main', status: 'ACTIVE', hasElevator: false, ...overrides }
}

function floor(overrides: Partial<Floor> = {}): Floor {
  return { id: 'f1', buildingId: 'b1', name: '1', status: 'ACTIVE', groundFloor: false, ...overrides }
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

    await user.type(screen.getByLabelText(/name des neuen gebäudes/i), 'Main')
    await user.click(screen.getByRole('button', { name: /gebäude hinzufügen/i }))

    await waitFor(() => expect(buildings.createBuilding).toHaveBeenCalledWith('Main', false))
    await screen.findByText('Main')

    await user.type(screen.getByLabelText(/name des neuen stockwerks/i), '1')
    await user.click(screen.getByRole('button', { name: /stockwerk zu main hinzufügen/i }))

    await waitFor(() => expect(floors.createFloor).toHaveBeenCalledWith('b1', '1', false))
  })

  it('renames a building and a floor', async () => {
    const user = userEvent.setup()
    buildings.listBuildings.mockResolvedValue([building()])
    floors.listFloors.mockResolvedValue([floor()])
    buildings.renameBuilding.mockResolvedValue(building({ name: 'Main Building' }))
    floors.renameFloor.mockResolvedValue(floor({ name: 'Ground' }))

    render(<BuildingCatalog />)
    await screen.findByText('Main')

    await user.clear(screen.getByLabelText(/gebäude main umbenennen/i))
    await user.type(screen.getByLabelText(/gebäude main umbenennen/i), 'Main Building')
    await user.click(screen.getByRole('button', { name: /name des gebäudes main speichern/i }))
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

    await user.click(screen.getByRole('button', { name: /gebäude main deaktivieren/i }))

    await waitFor(() => expect(buildings.deactivateBuilding).toHaveBeenCalledWith('b1'))
    expect(await screen.findAllByText(/deaktiviert/i)).not.toHaveLength(0)
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

    await user.click(screen.getByRole('button', { name: /gebäude main löschen/i }))

    await screen.findByText(/still has one or more floors/i)
    expect(screen.getByText('Main')).toBeInTheDocument()
  })

  it('creates a building with an elevator when "Aufzug vorhanden" is checked', async () => {
    const user = userEvent.setup()
    buildings.createBuilding.mockResolvedValue(building({ hasElevator: true }))

    render(<BuildingCatalog />)

    await user.type(screen.getByLabelText(/name des neuen gebäudes/i), 'Main')
    await user.click(screen.getByLabelText('Aufzug vorhanden'))
    await user.click(screen.getByRole('button', { name: /gebäude hinzufügen/i }))

    await waitFor(() => expect(buildings.createBuilding).toHaveBeenCalledWith('Main', true))
  })

  it('shows and toggles the elevator of a building', async () => {
    const user = userEvent.setup()
    buildings.listBuildings.mockResolvedValue([building({ hasElevator: false })])
    buildings.updateBuilding.mockResolvedValue(building({ hasElevator: true }))

    render(<BuildingCatalog />)

    expect(await screen.findByText('kein Aufzug')).toBeInTheDocument()
    await user.click(screen.getByRole('checkbox', { name: 'Aufzug in Main' }))

    await waitFor(() => expect(buildings.updateBuilding).toHaveBeenCalledWith('b1', 'Main', true))
  })

  it('creates a ground floor and toggles the ground-floor mark of a floor', async () => {
    const user = userEvent.setup()
    buildings.listBuildings.mockResolvedValue([building()])
    floors.listFloors.mockResolvedValue([floor({ name: 'EG', groundFloor: true })])
    floors.createFloor.mockResolvedValue(floor())
    floors.updateFloor.mockResolvedValue(floor({ name: 'EG', groundFloor: false }))

    render(<BuildingCatalog />)
    await screen.findByText('Main')

    expect(screen.getByText('Erdgeschoss', { selector: '.catalog-tag' })).toBeInTheDocument()
    await user.type(screen.getByLabelText(/name des neuen stockwerks/i), '1. OG')
    await user.click(screen.getByLabelText('Erdgeschoss (stufenloser Zugang)'))
    await user.click(screen.getByRole('button', { name: /stockwerk zu main hinzufügen/i }))
    await waitFor(() => expect(floors.createFloor).toHaveBeenCalledWith('b1', '1. OG', true))

    await user.click(screen.getByRole('checkbox', { name: /erdgeschoss\s*:\s*eg/i }))
    await waitFor(() => expect(floors.updateFloor).toHaveBeenCalledWith('f1', 'EG', false))
  })
})

import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { EquipmentCatalog } from './EquipmentCatalog'
import * as equipmentApi from '../../API/equipmentTypes'
import { ApiError } from '../../API/client'
import type { EquipmentType } from '../../types/room'

vi.mock('../../API/equipmentTypes')

const equipment = vi.mocked(equipmentApi)

function type(overrides: Partial<EquipmentType> = {}): EquipmentType {
  return { id: 'e1', name: 'Projector', status: 'ACTIVE', ...overrides }
}

beforeEach(() => {
  vi.resetAllMocks()
  equipment.listEquipmentTypes.mockResolvedValue([])
})

describe('EquipmentCatalog', () => {
  it('creates an equipment type', async () => {
    const user = userEvent.setup()
    equipment.listEquipmentTypes.mockResolvedValueOnce([]).mockResolvedValue([type()])
    equipment.createEquipmentType.mockResolvedValue(type())

    render(<EquipmentCatalog />)

    await user.type(screen.getByLabelText(/new equipment type name/i), 'Projector')
    await user.click(screen.getByRole('button', { name: /add equipment type/i }))

    await waitFor(() => expect(equipment.createEquipmentType).toHaveBeenCalledWith('Projector'))
    await screen.findByText('Projector')
  })

  it('renames an equipment type', async () => {
    const user = userEvent.setup()
    equipment.listEquipmentTypes.mockResolvedValue([type()])
    equipment.renameEquipmentType.mockResolvedValue(type({ name: 'Beamer' }))

    render(<EquipmentCatalog />)
    await screen.findByText('Projector')

    await user.clear(screen.getByLabelText(/rename equipment type projector/i))
    await user.type(screen.getByLabelText(/rename equipment type projector/i), 'Beamer')
    await user.click(screen.getByRole('button', { name: /save equipment type name/i }))

    await waitFor(() => expect(equipment.renameEquipmentType).toHaveBeenCalledWith('e1', 'Beamer'))
  })

  it('deactivates an equipment type', async () => {
    const user = userEvent.setup()
    equipment.listEquipmentTypes.mockResolvedValue([type()])
    equipment.deactivateEquipmentType.mockResolvedValue(type({ status: 'DEACTIVATED' }))

    render(<EquipmentCatalog />)
    await screen.findByText('Projector')

    await user.click(screen.getByRole('button', { name: /deactivate equipment type projector/i }))

    await waitFor(() => expect(equipment.deactivateEquipmentType).toHaveBeenCalledWith('e1'))
  })

  it('shows an explanatory message when delete is blocked because the type is assigned', async () => {
    const user = userEvent.setup()
    equipment.listEquipmentTypes.mockResolvedValue([type()])
    equipment.deleteEquipmentType.mockRejectedValue(
      new ApiError(409, {
        title: 'Conflict',
        status: 409,
        detail: "Equipment type 'Projector' is assigned to one or more rooms; deactivate it instead.",
      }),
    )

    render(<EquipmentCatalog />)
    await screen.findByText('Projector')

    await user.click(screen.getByRole('button', { name: /delete equipment type projector/i }))

    await screen.findByText(/assigned to one or more rooms/i)
  })
})

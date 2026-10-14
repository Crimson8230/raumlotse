import { render, screen, waitFor, within } from '@testing-library/react'
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

    await user.type(screen.getByLabelText(/name des neuen ausstattungstyps/i), 'Projector')
    await user.click(screen.getByRole('button', { name: /ausstattungstyp hinzufügen/i }))

    await waitFor(() => expect(equipment.createEquipmentType).toHaveBeenCalledWith('Projector'))
    await screen.findByText('Projector')
  })

  it('creates a specialised equipment type when the checkbox is ticked (feature 017)', async () => {
    const user = userEvent.setup()
    equipment.createEquipmentType.mockResolvedValue(type({ name: 'PC-Arbeitsplätze', specialised: true }))

    render(<EquipmentCatalog />)

    const checkbox = screen.getByRole('checkbox', { name: 'Spezialausstattung (wirkt sich auf die Sortierung aus)' })
    expect(checkbox).not.toBeChecked()
    await user.type(screen.getByLabelText(/name des neuen ausstattungstyps/i), 'PC-Arbeitsplätze')
    await user.click(checkbox)
    await user.click(screen.getByRole('button', { name: /ausstattungstyp hinzufügen/i }))

    await waitFor(() =>
      expect(equipment.createEquipmentType).toHaveBeenCalledWith('PC-Arbeitsplätze', undefined, true),
    )
    expect(screen.getByRole('checkbox', { name: 'Spezialausstattung (wirkt sich auf die Sortierung aus)' })).not.toBeChecked()
  })

  it('marks specialised types with a badge and reclassifies an existing type', async () => {
    const user = userEvent.setup()
    equipment.listEquipmentTypes.mockResolvedValue([
      type({ specialised: true, name: 'PC-Arbeitsplätze', id: 'e2' }),
      type(),
    ])
    equipment.renameEquipmentType.mockResolvedValue(type({ specialised: true }))

    render(<EquipmentCatalog />)
    await screen.findByText('Projector')

    expect(screen.getAllByText('Spezial')).toHaveLength(1)
    const projector = screen.getByRole('checkbox', { name: 'Projector ist Spezialausstattung' })
    expect(projector).not.toBeChecked()
    expect(screen.getByRole('checkbox', { name: 'PC-Arbeitsplätze ist Spezialausstattung' })).toBeChecked()

    await user.click(projector)

    await waitFor(() => expect(equipment.renameEquipmentType).toHaveBeenCalledWith('e1', 'Projector', undefined, true))
  })

  it('renames an equipment type', async () => {
    const user = userEvent.setup()
    equipment.listEquipmentTypes.mockResolvedValue([type()])
    equipment.renameEquipmentType.mockResolvedValue(type({ name: 'Beamer' }))

    render(<EquipmentCatalog />)
    await screen.findByText('Projector')

    await user.clear(screen.getByLabelText(/ausstattungstyp projector umbenennen/i))
    await user.type(screen.getByLabelText(/ausstattungstyp projector umbenennen/i), 'Beamer')
    await user.click(screen.getByRole('button', { name: /name des ausstattungstyps projector speichern/i }))

    await waitFor(() => expect(equipment.renameEquipmentType).toHaveBeenCalledWith('e1', 'Beamer'))
  })

  it('deactivates an equipment type', async () => {
    const user = userEvent.setup()
    equipment.listEquipmentTypes.mockResolvedValue([type()])
    equipment.deactivateEquipmentType.mockResolvedValue(type({ status: 'DEACTIVATED' }))

    render(<EquipmentCatalog />)
    await screen.findByText('Projector')

    await user.click(screen.getByRole('button', { name: /ausstattungstyp projector deaktivieren/i }))

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

    await user.click(screen.getByRole('button', { name: /ausstattungstyp projector löschen/i }))

    await screen.findByText(/assigned to one or more rooms/i)
  })
})

describe('EquipmentCatalog list structure and fields (feature 019)', () => {
  it('shows records as real list items and the empty state as text', async () => {
    equipment.listEquipmentTypes.mockResolvedValue([type(), type({ id: 'e2', name: 'Whiteboard' })])
    render(<EquipmentCatalog />)
    expect(await screen.findAllByRole('listitem')).toHaveLength(2)
  })

  it('says so in words when there are no equipment types', async () => {
    render(<EquipmentCatalog />)
    expect(await screen.findByText('Noch keine Ausstattungstypen.')).toBeInTheDocument()
    expect(screen.queryByRole('list')).not.toBeInTheDocument()
  })

  it('marks the new-name field as required and the add button as primary', () => {
    render(<EquipmentCatalog />)
    expect(screen.getByLabelText(/name des neuen ausstattungstyps/i)).toHaveAttribute('aria-required', 'true')
    expect(screen.getByRole('button', { name: /ausstattungstyp hinzufügen/i })).toHaveClass('btn-primary')
  })
})

describe('EquipmentCatalog lists (feature 020)', () => {
  it('exposes the types as a named list and shows an empty text without records', async () => {
    equipment.listEquipmentTypes.mockResolvedValue([type(), type({ id: 'e2', name: 'Whiteboard' })])
    const { unmount } = render(<EquipmentCatalog />)

    const list = await screen.findByRole('list', { name: 'Ausstattungstypen' })
    expect(within(list).getAllByRole('listitem')).toHaveLength(2)
    unmount()

    equipment.listEquipmentTypes.mockResolvedValue([])
    render(<EquipmentCatalog />)
    expect(await screen.findByText('Noch keine Ausstattungstypen.')).toBeInTheDocument()
  })
})

import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { RoomSearchPanel } from './RoomSearchPanel'
import { emptySearchFormState } from '../../types/room'
import type { Building, EquipmentType } from '../../types/room'

const buildings: Building[] = [
  { id: 'b1', name: 'Haus 1', status: 'ACTIVE', hasElevator: true },
  { id: 'b2', name: 'Haus 2', status: 'ACTIVE', hasElevator: false },
]

const equipmentTypes: EquipmentType[] = [
  { id: 'e1', name: 'Projector', status: 'ACTIVE' },
  { id: 'e2', name: 'Whiteboard', status: 'ACTIVE' },
]

function renderPanel(overrides: Partial<Parameters<typeof RoomSearchPanel>[0]> = {}) {
  const props = {
    value: emptySearchFormState,
    buildings,
    seatingArrangementNames: ['Theater', 'U-Shape'],
    equipmentTypes,
    onSearch: vi.fn(),
    onReset: vi.fn(),
    ...overrides,
  }
  render(<RoomSearchPanel {...props} />)
  return props
}

describe('RoomSearchPanel', () => {
  it('renders labeled person inputs and a building select with all active buildings', () => {
    renderPanel()

    expect(screen.getByLabelText('Personen min.')).toBeInTheDocument()
    expect(screen.getByLabelText('Personen max.')).toBeInTheDocument()
    const select = screen.getByLabelText('Gebäude')
    expect(select).toHaveDisplayValue('Alle Gebäude')
    expect(screen.getByRole('option', { name: 'Haus 1' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'Haus 2' })).toBeInTheDocument()
  })

  it('starts from the given value', () => {
    renderPanel({ value: { ...emptySearchFormState, minPersons: '20', buildingId: 'b2' } })

    expect(screen.getByLabelText('Personen min.')).toHaveValue(20)
    expect(screen.getByLabelText('Gebäude')).toHaveValue('b2')
  })

  it('calls onSearch with the entered filters', async () => {
    const user = userEvent.setup()
    const props = renderPanel()

    await user.type(screen.getByLabelText('Personen min.'), '20')
    await user.type(screen.getByLabelText('Personen max.'), '50')
    await user.selectOptions(screen.getByLabelText('Gebäude'), 'b1')
    await user.click(screen.getByRole('button', { name: 'Suchen' }))

    expect(props.onSearch).toHaveBeenCalledWith({
      ...emptySearchFormState,
      minPersons: '20',
      maxPersons: '50',
      buildingId: 'b1',
    })
  })

  it('shows an inline error and does not search when the input is invalid', async () => {
    const user = userEvent.setup()
    const props = renderPanel()

    await user.type(screen.getByLabelText('Personen min.'), '30')
    await user.type(screen.getByLabelText('Personen max.'), '10')
    await user.click(screen.getByRole('button', { name: 'Suchen' }))

    expect(screen.getByText('Min. Personen darf nicht größer als Max. Personen sein.')).toHaveClass('feedback-error')
    expect(props.onSearch).not.toHaveBeenCalled()
  })

  it('rejects zero persons', async () => {
    const user = userEvent.setup()
    const props = renderPanel()

    await user.type(screen.getByLabelText('Personen min.'), '0')
    await user.click(screen.getByRole('button', { name: 'Suchen' }))

    expect(screen.getByText('Bitte eine ganze Zahl ab 1 eingeben.')).toBeInTheDocument()
    expect(props.onSearch).not.toHaveBeenCalled()
  })

  it('resets all filters in one action', async () => {
    const user = userEvent.setup()
    const props = renderPanel({ value: { ...emptySearchFormState, minPersons: '20' } })

    await user.click(screen.getByRole('button', { name: 'Filter zurücksetzen' }))

    expect(props.onReset).toHaveBeenCalledTimes(1)
  })

  it('disables every control and explains why when disabled', () => {
    renderPanel({ disabled: true })

    expect(screen.getByText('Die Suche umfasst nur aktive Räume.')).toBeInTheDocument()
    expect(screen.getByLabelText('Personen min.')).toBeDisabled()
    expect(screen.getByLabelText('Gebäude')).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Suchen' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Filter zurücksetzen' })).toBeDisabled()
  })

  it('offers all seating arrangement names plus "Alle"', () => {
    renderPanel()

    expect(screen.getByLabelText('Bestuhlung')).toHaveDisplayValue('Alle')
    expect(screen.getByRole('option', { name: 'Theater' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'U-Shape' })).toBeInTheDocument()
  })

  it('offers one equipment checkbox per given equipment type', () => {
    renderPanel()

    const group = screen.getByRole('group', { name: 'Ausstattung' })
    expect(within(group).getAllByRole('checkbox')).toHaveLength(2)
    expect(within(group).getByRole('checkbox', { name: 'Projector' })).not.toBeChecked()
  })

  it('searches with the selected seating arrangement and equipment', async () => {
    const user = userEvent.setup()
    const props = renderPanel({ value: { ...emptySearchFormState, equipmentTypeIds: ['e2'] } })

    expect(screen.getByRole('checkbox', { name: 'Whiteboard' })).toBeChecked()
    await user.selectOptions(screen.getByLabelText('Bestuhlung'), 'U-Shape')
    await user.click(screen.getByRole('checkbox', { name: 'Projector' }))
    await user.click(screen.getByRole('checkbox', { name: 'Whiteboard' }))
    await user.click(screen.getByRole('button', { name: 'Suchen' }))

    expect(props.onSearch).toHaveBeenCalledWith({
      ...emptySearchFormState,
      seatingArrangement: 'U-Shape',
      equipmentTypeIds: ['e1'],
    })
  })

  it('searches for barrier-free reachable rooms when the checkbox is checked', async () => {
    const user = userEvent.setup()
    const props = renderPanel()

    await user.click(screen.getByRole('checkbox', { name: 'Barrierefrei erreichbar' }))
    await user.click(screen.getByRole('button', { name: 'Suchen' }))

    expect(props.onSearch).toHaveBeenCalledWith({ ...emptySearchFormState, barrierFree: true })
  })

  it('offers date and time inputs and searches with the window', async () => {
    const user = userEvent.setup()
    const props = renderPanel()

    expect(screen.getByLabelText('Datum')).toHaveAttribute('type', 'date')
    expect(screen.getByLabelText('Von')).toHaveAttribute('type', 'time')
    expect(screen.getByLabelText('Bis')).toHaveAttribute('type', 'time')

    await user.type(screen.getByLabelText('Datum'), '2026-10-05')
    await user.type(screen.getByLabelText('Von'), '11:00')
    await user.type(screen.getByLabelText('Bis'), '12:00')
    await user.click(screen.getByRole('button', { name: 'Suchen' }))

    expect(props.onSearch).toHaveBeenCalledWith({
      ...emptySearchFormState,
      date: '2026-10-05',
      startTime: '11:00',
      endTime: '12:00',
    })
  })

  it('shows the window errors inline and does not search', async () => {
    const user = userEvent.setup()
    const props = renderPanel({ value: { ...emptySearchFormState, date: '2026-10-05', startTime: '12:00', endTime: '11:00' } })

    await user.click(screen.getByRole('button', { name: 'Suchen' }))

    expect(screen.getByText('Bis muss nach Von liegen.')).toHaveClass('feedback-error')
    expect(props.onSearch).not.toHaveBeenCalled()
  })
})

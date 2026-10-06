import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { MapSelector } from './MapSelector'
import type { MapSummary } from '../../types/map'

function map(id: string, name: string): MapSummary {
  return { id, floorId: `f-${id}`, name, widthPx: 100, heightPx: 50, imageVersion: 1, placedRoomCount: 0 }
}

describe('MapSelector', () => {
  it('lists all maps and reports the selection', async () => {
    const onSelect = vi.fn()
    render(<MapSelector maps={[map('m1', 'Haus A – EG'), map('m2', 'Haus A – 1. OG')]} selectedId="m1" onSelect={onSelect} />)

    expect(screen.getByRole('option', { name: 'Haus A – EG' })).toBeInTheDocument()
    await userEvent.selectOptions(screen.getByLabelText(/karte/i), 'm2')
    expect(onSelect).toHaveBeenCalledWith('m2')
  })

  it('shows an explanatory empty state when no map exists', () => {
    render(<MapSelector maps={[]} selectedId={undefined} onSelect={vi.fn()} />)
    expect(screen.getByText(/noch keine karte/i)).toBeInTheDocument()
    expect(screen.queryByLabelText(/karte/i)).not.toBeInTheDocument()
  })
})

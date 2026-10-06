import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ConnectionLayer } from './ConnectionLayer'
import type { Connection, MapSummary } from '../../types/map'

const maps: MapSummary[] = [
  { id: 'm0', floorId: 'f0', name: 'Haus A – EG', widthPx: 1, heightPx: 1, imageVersion: 1, placedRoomCount: 0 },
  { id: 'm1', floorId: 'f1', name: 'Haus A – 1. OG', widthPx: 1, heightPx: 1, imageVersion: 1, placedRoomCount: 0 },
  { id: 'm2', floorId: 'f2', name: 'Haus A – 2. OG', widthPx: 1, heightPx: 1, imageVersion: 1, placedRoomCount: 0 },
]
const elevator: Connection = {
  id: 'c1', name: 'Aufzug A', type: 'ELEVATOR', incomplete: false,
  points: [{ mapId: 'm0', x: 0.2, y: 0.3 }, { mapId: 'm1', x: 0.6, y: 0.7 }, { mapId: 'm2', x: 0.1, y: 0.1 }],
}
const stairs: Connection = {
  id: 'c2', name: 'Treppe Nord', type: 'STAIRS', incomplete: true, points: [{ mapId: 'm0', x: 0.9, y: 0.5 }],
}

describe('ConnectionLayer', () => {
  it('shows only the points on the current map, labeled with name and type', () => {
    render(<ConnectionLayer connections={[elevator, stairs]} currentMapId="m1" maps={maps} onNavigate={vi.fn()} />)

    const marker = screen.getByRole('button', { name: /aufzug a \(aufzug\)/i })
    expect(marker).toHaveStyle({ left: '60%', top: '70%' })
    expect(screen.queryByRole('button', { name: /treppe nord/i })).not.toBeInTheDocument()
  })

  it('distinguishes stairs from elevators by label and style class', () => {
    render(<ConnectionLayer connections={[elevator, stairs]} currentMapId="m0" maps={maps} onNavigate={vi.fn()} />)
    expect(screen.getByRole('button', { name: /aufzug a \(aufzug\)/i })).toHaveClass('connection-marker--elevator')
    expect(screen.getByRole('button', { name: /treppe nord \(treppe\)/i })).toHaveClass('connection-marker--stairs')
  })

  it('names the other maps a connection reaches and navigates to them', async () => {
    const onNavigate = vi.fn()
    render(<ConnectionLayer connections={[elevator]} currentMapId="m0" maps={maps} onNavigate={onNavigate} />)

    await userEvent.click(screen.getByRole('button', { name: /aufzug a/i }))

    expect(screen.queryByRole('button', { name: 'Haus A – EG' })).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Haus A – 2. OG' }))
    expect(screen.getByRole('button', { name: 'Haus A – 1. OG' })).toBeInTheDocument()
    expect(onNavigate).toHaveBeenCalledWith('m2')
  })

  it('flags a connection that does not yet span two maps as incomplete', async () => {
    render(<ConnectionLayer connections={[stairs]} currentMapId="m0" maps={maps} onNavigate={vi.fn()} />)
    await userEvent.click(screen.getByRole('button', { name: /treppe nord/i }))
    expect(screen.getByText(/unvollständig/i)).toBeInTheDocument()
  })
})

import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { RoomOverview } from './RoomOverview'
import type { Room } from '../../types/room'

function room(overrides: Partial<Room> = {}): Room {
  return {
    id: 'r1',
    name: 'Raum 2.14',
    building: { id: 'b1', name: 'Haus A', status: 'ACTIVE', hasElevator: true },
    floor: { id: 'f1', buildingId: 'b1', name: '2. OG', status: 'ACTIVE', groundFloor: false },
    status: 'ACTIVE',
    version: 0,
    seatingArrangements: [
      { name: 'Reihen', maxCapacity: 40 },
      { name: 'U-Form', maxCapacity: 20 },
    ],
    equipmentTypeIds: ['e1', 'e2'],
    notBarrierFree: false,
    barrierFreeReachable: true,
    ...overrides,
  }
}

describe('RoomOverview', () => {
  it('shows seating, equipment and access with labels', () => {
    render(<RoomOverview room={room()} equipmentNames={{ e1: 'Beamer', e2: 'Whiteboard' }} />)

    expect(screen.getByRole('heading', { name: 'Raum auf einen Blick' })).toBeInTheDocument()
    expect(screen.getByText('Sitzordnungen').nextElementSibling).toHaveTextContent('Reihen (max 40), U-Form (max 20)')
    expect(screen.getByText('Ausstattung').nextElementSibling).toHaveTextContent('Beamer, Whiteboard')
    const access = screen.getByText('Zugang').nextElementSibling as HTMLElement
    expect(within(access).getByText(/Barrierefrei erreichbar: Ja/)).toBeInTheDocument()
    expect(within(access).getByText(/Aufzug im Gebäude: Ja/)).toBeInTheDocument()
    expect(within(access).getByText(/Ebene ohne Stufen: Nein/)).toBeInTheDocument()
  })

  it('reads "keine Angabe" when seating or equipment are missing', () => {
    render(<RoomOverview room={room({ seatingArrangements: [], equipmentTypeIds: [] })} equipmentNames={{}} />)

    expect(screen.getByText('Sitzordnungen').nextElementSibling).toHaveTextContent('keine Angabe')
    expect(screen.getByText('Ausstattung').nextElementSibling).toHaveTextContent('keine Angabe')
  })

  it('falls back to the id for an unknown equipment type', () => {
    render(<RoomOverview room={room({ equipmentTypeIds: ['e9'] })} equipmentNames={{}} />)

    expect(screen.getByText('Ausstattung').nextElementSibling).toHaveTextContent('e9')
  })
})

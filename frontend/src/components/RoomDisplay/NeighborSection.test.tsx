import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { NeighborSection } from './NeighborSection'
import type { NeighborEntry, Neighbors } from '../../types/routing'

function entry(overrides: Partial<NeighborEntry> = {}): NeighborEntry {
  return {
    direction: 'LEFT',
    kind: 'ROOM',
    roomId: 'r2',
    connectionId: null,
    name: 'Raum 2.14',
    targetFloorName: null,
    distanceMeters: 6.4,
    walkingSeconds: 5,
    status: null,
    ...overrides,
  }
}

function neighbors(entries: NeighborEntry[], overrides: Partial<Neighbors> = {}): Neighbors {
  return { roomId: 'r1', available: true, reason: null, entries, ...overrides }
}

describe('NeighborSection', () => {
  it('renders one Wayfinding per entry with German direction, target, distance and walking time', () => {
    render(
      <NeighborSection
        neighbors={neighbors([
          entry(),
          entry({ direction: 'RIGHT', roomId: 'r3', name: 'Raum 2.16', distanceMeters: 14, walkingSeconds: 12 }),
          entry({ direction: 'STRAIGHT', roomId: 'r4', name: 'Raum 2.20', distanceMeters: 80, walkingSeconds: 67 }),
        ])}
      />,
    )

    const section = screen.getByRole('region', { name: 'Nachbarräume' })
    const items = within(section).getAllByRole('listitem')
    expect(items).toHaveLength(3)
    expect(items[0]).toHaveTextContent('Raum 2.14 · links · ca. 6 m (5 s)')
    expect(items[1]).toHaveTextContent('Raum 2.16 · rechts · ca. 14 m (12 s)')
    expect(items[2]).toHaveTextContent('Raum 2.20 · geradeaus · ca. 80 m (1 Min.)')
  })

  it('labels stairs and elevators with their type, target floor and vertical direction', () => {
    render(
      <NeighborSection
        neighbors={neighbors([
          entry({ direction: 'UP', kind: 'STAIRS', roomId: null, connectionId: 'c1', name: 'Treppe A', targetFloorName: '3. OG' }),
          entry({ direction: 'DOWN', kind: 'ELEVATOR', roomId: null, connectionId: 'c2', name: 'Aufzug 1', targetFloorName: 'EG' }),
        ])}
      />,
    )

    expect(screen.getByText('Treppe A (3. OG)')).toBeInTheDocument()
    expect(screen.getByText('Aufzug 1 (EG)')).toBeInTheDocument()
    expect(screen.getByText('hoch')).toBeInTheDocument()
    expect(screen.getByText('runter')).toBeInTheDocument()
  })

  it('shows the occupancy status of neighbor rooms when there is one', () => {
    render(
      <NeighborSection
        neighbors={neighbors([
          entry({ status: 'OCCUPIED' }),
          entry({ roomId: 'r3', name: 'Raum 2.16', direction: 'RIGHT', status: 'AVAILABLE' }),
          entry({ roomId: 'r4', name: 'Raum 2.18', direction: 'RIGHT', status: 'RESERVED' }),
          entry({ roomId: 'r5', name: 'Raum 2.19', direction: 'RIGHT', status: null }),
        ])}
      />,
    )

    expect(screen.getByText('Belegt')).toBeInTheDocument()
    expect(screen.getByText('Verfügbar')).toBeInTheDocument()
    expect(screen.getByText('Reserviert')).toBeInTheDocument()
    expect(screen.getAllByText(/Belegt|Verfügbar|Reserviert/)).toHaveLength(3)
  })

  it('shortens very long names', () => {
    const name = 'Seminarraum für interdisziplinäre Forschungsprojekte'
    render(<NeighborSection neighbors={neighbors([entry({ name })])} />)
    const shown = screen.getByText(/^Seminarraum/)
    expect(shown.textContent!.length).toBeLessThanOrEqual(28)
    expect(shown.textContent).toMatch(/…$/)
  })

  it.each([
    ['null', null],
    ['unavailable', neighbors([], { available: false, reason: 'ROOM_NOT_ATTACHED' })],
    ['empty', neighbors([])],
  ])('renders nothing when the neighbors are %s', (_label, value) => {
    const { container } = render(<NeighborSection neighbors={value} />)
    expect(container).toBeEmptyDOMElement()
  })
})

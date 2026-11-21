import { act, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { PathOverlay } from './PathOverlay'
import { sampleNetwork } from './fixtures'

// jsdom has no layout: the overlay is 200 x 100 at the origin.
beforeEach(() => {
  vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockReturnValue({
    x: 0, y: 0, left: 0, top: 0, right: 200, bottom: 100, width: 200, height: 100, toJSON: () => ({}),
  })
})
afterEach(() => vi.restoreAllMocks())

const base = { rooms: [], connectionPoints: [], referenceDraft: [], highlightParts: false }

describe('PathOverlay', () => {
  it('draws waypoints and segments in the 0..1 coordinate space of the map', () => {
    const { container } = render(<PathOverlay {...base} network={sampleNetwork()} tool="view" editable={false} />)
    expect(container.querySelector('svg')).toHaveAttribute('viewBox', '0 0 1 1')
    expect(screen.getByRole('button', { name: 'Wegpunkt 1' })).toHaveStyle({ left: '10%', top: '50%' })
    expect(screen.getByRole('button', { name: 'Wegpunkt 3' })).toHaveStyle({ left: '90%', top: '50%' })
    expect(container.querySelectorAll('line.path-segment')).toHaveLength(2)
  })

  it('shows the length of a selected segment in meters, or that it is unavailable without a scale', () => {
    const { container, rerender } = render(<PathOverlay {...base} network={sampleNetwork()} tool="view" editable={false} />)
    fireEvent.click(container.querySelector('line[data-segment-id="s2"]')!)
    expect(screen.getByRole('status')).toHaveTextContent('12,5 m')

    const noScale = sampleNetwork({
      scale: { available: false, pixelsPerMeter: null, widthPx: 200, heightPx: 100 },
      referenceSegment: null,
      segments: [{ id: 's1', mapId: 'm1', fromWaypointId: 'w1', toWaypointId: 'w2', lengthMeters: null }],
    })
    rerender(<PathOverlay {...base} network={noScale} tool="view" editable={false} />)
    fireEvent.click(container.querySelector('line[data-segment-id="s1"]')!)
    expect(screen.getByRole('status')).toHaveTextContent(/nicht verfügbar/i)
  })

  it('reports clicks on waypoints so the page can connect, delete or attach them', () => {
    const onWaypointClick = vi.fn()
    render(<PathOverlay {...base} network={sampleNetwork()} tool="connect" editable onWaypointClick={onWaypointClick} />)
    fireEvent.click(screen.getByRole('button', { name: 'Wegpunkt 2' }))
    expect(onWaypointClick).toHaveBeenCalledWith('w2')
  })

  it('moves a waypoint by dragging in move mode and emits relative coordinates', () => {
    const onWaypointMove = vi.fn()
    render(<PathOverlay {...base} network={sampleNetwork()} tool="move" editable onWaypointMove={onWaypointMove} />)
    fireEvent.pointerDown(screen.getByRole('button', { name: 'Wegpunkt 2' }), { clientX: 100, clientY: 50 })
    act(() => {
      window.dispatchEvent(new MouseEvent('pointerup', { clientX: 150, clientY: 25, bubbles: true }))
    })
    expect(onWaypointMove).toHaveBeenCalledWith('w2', { x: 0.75, y: 0.25 })
  })

  it('does not drag when the user may not edit', () => {
    const onWaypointMove = vi.fn()
    render(<PathOverlay {...base} network={sampleNetwork()} tool="move" editable={false} onWaypointMove={onWaypointMove} />)
    fireEvent.pointerDown(screen.getByRole('button', { name: 'Wegpunkt 2' }), { clientX: 100, clientY: 50 })
    act(() => {
      window.dispatchEvent(new MouseEvent('pointerup', { clientX: 150, clientY: 25, bubbles: true }))
    })
    expect(onWaypointMove).not.toHaveBeenCalled()
  })

  it('reports the clicked position on a segment for splitting and deleting', () => {
    const onSegmentClick = vi.fn()
    const { container } = render(
      <PathOverlay {...base} network={sampleNetwork()} tool="split" editable onSegmentClick={onSegmentClick} />,
    )
    fireEvent.click(container.querySelector('line[data-segment-id="s1"]')!, { clientX: 60, clientY: 50 })
    expect(onSegmentClick).toHaveBeenCalledWith('s1', { x: 0.3, y: 0.5 })
  })

  it('marks the first waypoint of a pending connection', () => {
    render(<PathOverlay {...base} network={sampleNetwork()} tool="connect" editable pendingFromId="w1" />)
    expect(screen.getByRole('button', { name: 'Wegpunkt 1 (Start der Verbindung)' })).toBeInTheDocument()
  })

  it('draws room and connection point attachments and flags unattached ones with text', () => {
    const network = sampleNetwork({
      roomAttachments: [{ roomId: 'r1', mapId: 'm1', waypointId: 'w1' }],
      issues: { ...sampleNetwork().issues, unattachedRoomIds: ['r2'], unattachedConnectionPointIds: ['p1'] },
    })
    const { container } = render(
      <PathOverlay
        {...base}
        network={network}
        tool="view"
        editable={false}
        rooms={[
          { id: 'r1', label: 'Raum 1', x: 0.1, y: 0.2 },
          { id: 'r2', label: 'Raum 2', x: 0.7, y: 0.2 },
        ]}
        connectionPoints={[{ id: 'p1', label: 'Aufzug A', x: 0.9, y: 0.8 }]}
      />,
    )
    expect(container.querySelectorAll('line.path-attachment')).toHaveLength(1)
    expect(screen.getByRole('img', { name: 'Raum 2: nicht angebunden' })).toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'Aufzug A: nicht angebunden' })).toBeInTheDocument()
    expect(screen.queryByRole('img', { name: 'Raum 1: nicht angebunden' })).not.toBeInTheDocument()
  })

  it('shows the reference segment with its length, distinct from walkways', () => {
    const { container } = render(<PathOverlay {...base} network={sampleNetwork()} tool="view" editable={false} />)
    expect(container.querySelectorAll('line.path-reference')).toHaveLength(1)
    expect(screen.getByText('Referenz: 10 m')).toBeInTheDocument()
  })

  it('shows the draft endpoints while a reference segment is being drawn', () => {
    const { container } = render(
      <PathOverlay {...base} network={sampleNetwork({ referenceSegment: null })} tool="reference" editable
        referenceDraft={[{ x: 0.2, y: 0.8 }, { x: 0.4, y: 0.8 }]} />,
    )
    expect(container.querySelectorAll('.path-reference-point')).toHaveLength(2)
    expect(container.querySelectorAll('line.path-reference--draft')).toHaveLength(1)
  })

  it('labels separate network parts when highlighting, without relying on colour', () => {
    const network = sampleNetwork({
      issues: { ...sampleNetwork().issues, componentCount: 2, components: [['w1', 'w2'], ['w3']] },
    })
    render(<PathOverlay {...base} network={network} tool="view" editable={false} highlightParts />)
    expect(screen.getByRole('button', { name: 'Wegpunkt 3 (Netzteil 2)' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Wegpunkt 1 (Netzteil 1)' })).toBeInTheDocument()
  })
})

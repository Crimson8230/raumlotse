import { act, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MapCanvas } from './MapCanvas'
import type { Placement } from '../../types/map'

const map = { id: 'm1', imageVersion: 3, name: 'Haus A – EG' }
const placements: Placement[] = [
  { room: { id: 'r1', name: 'Raum 1', status: 'ACTIVE' }, x: 0.25, y: 0.75 },
  { room: { id: 'r2', name: 'Raum 2', status: 'DEACTIVATED' }, x: 0.5, y: 0.5 },
]

// jsdom has no layout: the plane is 200 x 100 at the origin.
beforeEach(() => {
  vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockReturnValue({
    x: 0, y: 0, left: 0, top: 0, right: 200, bottom: 100, width: 200, height: 100, toJSON: () => ({}),
  })
})
afterEach(() => vi.restoreAllMocks())

describe('MapCanvas', () => {
  it('positions labeled markers as percentages of the image, independent of its pixel size', () => {
    render(<MapCanvas map={map} placements={placements} editable={false} />)

    const marker = screen.getByRole('button', { name: /raum 1/i })
    expect(marker).toHaveStyle({ left: '25%', top: '75%' })
    expect(screen.getByText('Raum 1')).toBeInTheDocument()
    expect(screen.getByAltText(/haus a – eg/i)).toHaveAttribute('src', '/api/maps/m1/image?v=3')
  })

  it('marks deactivated rooms with a text label, not by colour alone', () => {
    render(<MapCanvas map={map} placements={placements} editable={false} />)
    expect(screen.getByRole('button', { name: /raum 2.*deaktiviert/i })).toBeInTheDocument()
  })

  it('emits normalized coordinates when an admin clicks the image with a room selected', () => {
    const onPlace = vi.fn()
    render(<MapCanvas map={map} placements={[]} editable activeRoomId="r9" onPlace={onPlace} />)

    fireEvent.click(screen.getByTestId('map-plane'), { clientX: 50, clientY: 25 })

    expect(onPlace).toHaveBeenCalledWith({ x: 0.25, y: 0.25 })
  })

  it('shows a hint and emits nothing when the click lies outside the image', () => {
    const onPlace = vi.fn()
    render(<MapCanvas map={map} placements={[]} editable activeRoomId="r9" onPlace={onPlace} />)

    fireEvent.click(screen.getByTestId('map-plane'), { clientX: 250, clientY: 25 })

    expect(onPlace).not.toHaveBeenCalled()
    expect(screen.getByRole('status')).toHaveTextContent(/innerhalb der karte/i)
  })

  it('does not place anything without a selected room', () => {
    const onPlace = vi.fn()
    render(<MapCanvas map={map} placements={[]} editable onPlace={onPlace} />)
    fireEvent.click(screen.getByTestId('map-plane'), { clientX: 50, clientY: 25 })
    expect(onPlace).not.toHaveBeenCalled()
  })

  it('emits the new position when an admin drags a marker', () => {
    const onMove = vi.fn()
    render(<MapCanvas map={map} placements={placements} editable onMove={onMove} />)

    fireEvent.pointerDown(screen.getByRole('button', { name: /raum 1/i }), { clientX: 50, clientY: 75 })
    act(() => {
      window.dispatchEvent(new MouseEvent('pointerup', { clientX: 100, clientY: 50, bubbles: true }))
    })

    expect(onMove).toHaveBeenCalledWith('r1', { x: 0.5, y: 0.5 })
  })

  it('keeps a dragged marker inside the map when released outside', () => {
    const onMove = vi.fn()
    render(<MapCanvas map={map} placements={placements} editable onMove={onMove} />)

    fireEvent.pointerDown(screen.getByRole('button', { name: /raum 1/i }), { clientX: 50, clientY: 75 })
    act(() => {
      window.dispatchEvent(new MouseEvent('pointerup', { clientX: 300, clientY: -20, bubbles: true }))
    })

    expect(onMove).toHaveBeenCalledWith('r1', { x: 1, y: 0 })
  })

  it('is read-only for non-admins: no drag, no placement', () => {
    const onPlace = vi.fn()
    const onMove = vi.fn()
    render(<MapCanvas map={map} placements={placements} editable={false} activeRoomId="r9" onPlace={onPlace} onMove={onMove} />)

    fireEvent.pointerDown(screen.getByRole('button', { name: /raum 1/i }), { clientX: 50, clientY: 75 })
    act(() => {
      window.dispatchEvent(new MouseEvent('pointerup', { clientX: 100, clientY: 50, bubbles: true }))
    })
    fireEvent.click(screen.getByTestId('map-plane'), { clientX: 50, clientY: 25 })

    expect(onMove).not.toHaveBeenCalled()
    expect(onPlace).not.toHaveBeenCalled()
  })

  it('places a connection point without allowing room placement edits', () => {
    const onPlace = vi.fn()
    const onMove = vi.fn()
    const onRemove = vi.fn()
    render(<MapCanvas map={map} placements={placements} editable={false} placementActive
      onPlace={onPlace} onMove={onMove} onRemove={onRemove} />)
    fireEvent.pointerDown(screen.getByRole('button', { name: /raum 1/i }), { clientX: 50, clientY: 75 })
    act(() => window.dispatchEvent(new MouseEvent('pointerup', { clientX: 100, clientY: 50, bubbles: true })))
    fireEvent.click(screen.getByTestId('map-plane'), { clientX: 50, clientY: 25 })
    fireEvent.click(screen.getByRole('button', { name: /raum 1/i }))

    expect(onPlace).toHaveBeenCalledWith({ x: 0.25, y: 0.25 })
    expect(onMove).not.toHaveBeenCalled()
    expect(screen.queryByRole('button', { name: 'Platzierung entfernen' })).not.toBeInTheDocument()
  })

  it('shows name and identifier of a room when its marker is selected', () => {
    render(<MapCanvas map={map} placements={placements} editable={false} />)
    fireEvent.click(screen.getByRole('button', { name: /raum 1/i }))
    expect(screen.getByRole('region', { name: /raumdetails/i })).toHaveTextContent('r1')
  })

  it('zooms the plane for small screens', () => {
    render(<MapCanvas map={map} placements={placements} editable={false} />)
    fireEvent.click(screen.getByRole('button', { name: /vergrößern/i }))
    expect(screen.getByTestId('map-plane')).toHaveStyle({ width: '150%' })
    fireEvent.click(screen.getByRole('button', { name: /zurücksetzen/i }))
    expect(screen.getByTestId('map-plane')).toHaveStyle({ width: '100%' })
  })

  it('lets an admin nudge a focused marker with the arrow keys (keyboard alternative to dragging)', () => {
    const onMove = vi.fn()
    render(<MapCanvas map={map} placements={placements} editable onMove={onMove} />)
    const marker = screen.getByRole('button', { name: /raum 1/i })

    fireEvent.keyDown(marker, { key: 'ArrowRight' })
    expect(onMove).toHaveBeenLastCalledWith('r1', { x: 0.26, y: 0.75 })
    fireEvent.keyDown(marker, { key: 'ArrowUp', shiftKey: true })
    expect(onMove).toHaveBeenLastCalledWith('r1', { x: 0.25, y: 0.7 })
  })

  it('keeps nudged markers inside the map and ignores arrow keys for non-admins', () => {
    const onMove = vi.fn()
    const edge: Placement[] = [{ room: { id: 'r3', name: 'Raum 3', status: 'ACTIVE' }, x: 1, y: 0 }]
    const { rerender } = render(<MapCanvas map={map} placements={edge} editable onMove={onMove} />)

    fireEvent.keyDown(screen.getByRole('button', { name: /raum 3/i }), { key: 'ArrowRight' })
    fireEvent.keyDown(screen.getByRole('button', { name: /raum 3/i }), { key: 'ArrowUp' })
    expect(onMove).toHaveBeenLastCalledWith('r3', { x: 1, y: 0 })

    onMove.mockClear()
    rerender(<MapCanvas map={map} placements={edge} editable={false} onMove={onMove} />)
    fireEvent.keyDown(screen.getByRole('button', { name: /raum 3/i }), { key: 'ArrowLeft' })
    expect(onMove).not.toHaveBeenCalled()
  })

  it('exposes the zoom toolbar and the map plane to assistive technology', () => {
    render(<MapCanvas map={map} placements={placements} editable={false} />)
    expect(screen.getByRole('toolbar', { name: /zoom/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /raum 1/i })).toHaveAttribute('type', 'button')
  })
})

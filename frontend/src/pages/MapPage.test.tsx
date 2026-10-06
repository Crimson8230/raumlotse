import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import MapPage from './MapPage'
import * as mapsApi from '../API/maps'
import { ApiError } from '../API/client'
import * as buildingsApi from '../API/buildings'
import * as floorsApi from '../API/floors'
import { useCurrentRoles } from '../auth/useCurrentRoles'
import type { MapDetail, MapSummary } from '../types/map'

vi.mock('../API/maps')
vi.mock('../API/buildings')
vi.mock('../API/floors')
vi.mock('../auth/useCurrentRoles')

const maps = vi.mocked(mapsApi)
const roles = vi.mocked(useCurrentRoles)

function summary(id: string, name: string): MapSummary {
  return { id, floorId: `f-${id}`, name, widthPx: 100, heightPx: 50, imageVersion: 1, placedRoomCount: 0 }
}
function detail(id: string, name: string): MapDetail {
  return { ...summary(id, name), placements: [], connections: [] }
}

function renderPage(path = '/maps') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/maps" element={<MapPage />} />
        <Route path="/maps/:mapId" element={<MapPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  vi.resetAllMocks()
  roles.mockReturnValue({ loading: false, admin: false, failed: false })
  maps.mapImageUrl.mockImplementation((m) => `/api/maps/${m.id}/image?v=${m.imageVersion}`)
  maps.listUnplacedRooms.mockResolvedValue([])
  maps.listConnections.mockResolvedValue([])
  vi.mocked(buildingsApi).listBuildings.mockResolvedValue([])
  vi.mocked(floorsApi).listFloors.mockResolvedValue([])
})

describe('MapPage', () => {
  it('shows an explanatory empty state instead of an error when no map exists', async () => {
    maps.listMaps.mockResolvedValue([])
    renderPage()
    expect(await screen.findByText(/noch keine karte/i)).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('shows the first map and switches between maps', async () => {
    const user = userEvent.setup()
    maps.listMaps.mockResolvedValue([summary('m1', 'Haus A – EG'), summary('m2', 'Haus A – 1. OG')])
    maps.getMap.mockImplementation(async (id) => detail(id, id === 'm1' ? 'Haus A – EG' : 'Haus A – 1. OG'))
    renderPage()

    expect(await screen.findByAltText(/Haus A – EG/)).toHaveAttribute('src', '/api/maps/m1/image?v=1')
    await user.selectOptions(screen.getByLabelText(/karte/i), 'm2')
    expect(await screen.findByAltText(/Haus A – 1\. OG/)).toBeInTheDocument()
    expect(maps.getMap).toHaveBeenCalledWith('m2')
  })

  it('hides upload and delete controls from non-admins', async () => {
    maps.listMaps.mockResolvedValue([summary('m1', 'Haus A – EG')])
    maps.getMap.mockResolvedValue(detail('m1', 'Haus A – EG'))
    renderPage()
    await screen.findByAltText(/Haus A – EG/)
    expect(screen.queryByRole('button', { name: /karte löschen/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /bild ersetzen/i })).not.toBeInTheDocument()
  })

  it('lets admins delete a map after confirmation', async () => {
    const user = userEvent.setup()
    roles.mockReturnValue({ loading: false, admin: true, failed: false })
    maps.listMaps.mockResolvedValueOnce([summary('m1', 'Haus A – EG')]).mockResolvedValue([])
    maps.getMap.mockResolvedValue(detail('m1', 'Haus A – EG'))
    maps.deleteMap.mockResolvedValue(undefined)
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    renderPage()

    await user.click(await screen.findByRole('button', { name: /karte löschen/i }))

    await waitFor(() => expect(maps.deleteMap).toHaveBeenCalledWith('m1'))
    expect(await screen.findByText(/noch keine karte/i)).toBeInTheDocument()
  })

  it('reloads map data when the window regains focus so room changes show up', async () => {
    maps.listMaps.mockResolvedValue([summary('m1', 'Haus A – EG')])
    maps.getMap.mockResolvedValue(detail('m1', 'Haus A – EG'))
    renderPage()
    await screen.findByAltText(/Haus A – EG/)
    const before = maps.getMap.mock.calls.length

    window.dispatchEvent(new Event('focus'))

    await waitFor(() => expect(maps.getMap.mock.calls.length).toBeGreaterThan(before))
    expect(maps.listUnplacedRooms.mock.calls.length).toBeGreaterThan(1)
  })

  describe('placement', () => {
    const placed = { room: { id: 'r1', name: 'Raum 1', status: 'ACTIVE' as const }, x: 0.25, y: 0.5 }

    beforeEach(() => {
      maps.listMaps.mockResolvedValue([summary('m1', 'Haus A – EG')])
      maps.getMap.mockResolvedValue({ ...detail('m1', 'Haus A – EG'), placements: [placed] })
      maps.listUnplacedRooms.mockResolvedValue([{ id: 'r2', name: 'Raum 2', status: 'ACTIVE' }])
      vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockReturnValue({
        x: 0, y: 0, left: 0, top: 0, right: 200, bottom: 100, width: 200, height: 100, toJSON: () => ({}),
      })
    })

    it('shows placed rooms as labeled markers and the other rooms in the unplaced list', async () => {
      renderPage()
      expect(await screen.findByRole('button', { name: 'Raum 1' })).toBeInTheDocument()
      const list = await screen.findByRole('list', { name: /nicht platzierte räume/i })
      expect(within(list).getByText('Raum 2')).toBeInTheDocument()
    })

    it('lets an admin select an unplaced room and place it by clicking the map', async () => {
      const user = userEvent.setup()
      roles.mockReturnValue({ loading: false, admin: true, failed: false })
      maps.placeRoom.mockResolvedValue({ room: { id: 'r2', name: 'Raum 2', status: 'ACTIVE' }, x: 0.25, y: 0.25 })
      renderPage()

      await user.click(await screen.findByRole('button', { name: 'Raum 2' }))
      fireEvent.click(screen.getByTestId('map-plane'), { clientX: 50, clientY: 25 })

      await waitFor(() => expect(maps.placeRoom).toHaveBeenCalledWith('m1', 'r2', { x: 0.25, y: 0.25 }))
      await waitFor(() => expect(maps.getMap.mock.calls.length).toBeGreaterThan(1))
    })

    it('lets an admin remove the placement of the selected marker', async () => {
      const user = userEvent.setup()
      roles.mockReturnValue({ loading: false, admin: true, failed: false })
      maps.removePlacement.mockResolvedValue(undefined)
      renderPage()

      await user.click(await screen.findByRole('button', { name: 'Raum 1' }))
      await user.click(screen.getByRole('button', { name: /platzierung entfernen/i }))

      await waitFor(() => expect(maps.removePlacement).toHaveBeenCalledWith('m1', 'r1'))
    })

    it('shows no editing controls to non-admins', async () => {
      const user = userEvent.setup()
      renderPage()
      await user.click(await screen.findByRole('button', { name: 'Raum 1' }))
      expect(screen.queryByRole('button', { name: /platzierung entfernen/i })).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Raum 2' })).not.toBeInTheDocument()
    })

    it('shows the server error when a placement is rejected', async () => {
      const user = userEvent.setup()
      roles.mockReturnValue({ loading: false, admin: true, failed: false })
      maps.placeRoom.mockRejectedValue(new ApiError(422, { title: 'x', status: 422, detail: 'Room is on a different floor.' }))
      renderPage()

      await user.click(await screen.findByRole('button', { name: 'Raum 2' }))
      fireEvent.click(screen.getByTestId('map-plane'), { clientX: 50, clientY: 25 })

      expect(await screen.findByRole('alert')).toHaveTextContent('Room is on a different floor.')
    })
  })

  describe('connections', () => {
    const elevator = {
      id: 'c1', name: 'Aufzug A', type: 'ELEVATOR' as const, incomplete: false,
      points: [{ mapId: 'm1', x: 0.2, y: 0.3 }, { mapId: 'm2', x: 0.6, y: 0.7 }],
    }
    const stairs = { id: 'c2', name: 'Treppe Nord', type: 'STAIRS' as const, incomplete: true, points: [] }

    beforeEach(() => {
      maps.listMaps.mockResolvedValue([summary('m1', 'Haus A – EG'), summary('m2', 'Haus A – 1. OG')])
      maps.getMap.mockImplementation(async (id) => ({
        ...detail(id, id === 'm1' ? 'Haus A – EG' : 'Haus A – 1. OG'),
        connections: [elevator],
      }))
      maps.listConnections.mockResolvedValue([elevator, stairs])
      vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockReturnValue({
        x: 0, y: 0, left: 0, top: 0, right: 200, bottom: 100, width: 200, height: 100, toJSON: () => ({}),
      })
    })

    it('shows the connection markers of the current map and lets users jump to the reachable map', async () => {
      const user = userEvent.setup()
      renderPage('/maps/m1')

      await user.click(await screen.findByRole('button', { name: /aufzug a \(aufzug\)/i }))
      await user.click(screen.getByRole('button', { name: 'Haus A – 1. OG' }))

      expect(await screen.findByAltText(/Haus A – 1\. OG/)).toBeInTheDocument()
      expect(maps.getMap).toHaveBeenCalledWith('m2')
    })

    it('lets an admin place the point of a connection on the current map', async () => {
      const user = userEvent.setup()
      roles.mockReturnValue({ loading: false, admin: true, failed: false })
      maps.putConnectionPoint.mockResolvedValue(stairs)
      renderPage('/maps/m1')

      await user.click(await screen.findByRole('button', { name: /punkt setzen: treppe nord/i }))
      fireEvent.click(screen.getByTestId('map-plane'), { clientX: 100, clientY: 50 })

      await waitFor(() => expect(maps.putConnectionPoint).toHaveBeenCalledWith('c2', 'm1', { x: 0.5, y: 0.5 }))
      expect(maps.placeRoom).not.toHaveBeenCalled()
    })

    it('selecting a connection clears a selected room and vice versa', async () => {
      const user = userEvent.setup()
      roles.mockReturnValue({ loading: false, admin: true, failed: false })
      maps.listUnplacedRooms.mockResolvedValue([{ id: 'r2', name: 'Raum 2', status: 'ACTIVE' }])
      maps.placeRoom.mockResolvedValue({ room: { id: 'r2', name: 'Raum 2', status: 'ACTIVE' }, x: 0.5, y: 0.5 })
      renderPage('/maps/m1')

      await user.click(await screen.findByRole('button', { name: 'Raum 2' }))
      await user.click(screen.getByRole('button', { name: /punkt setzen: treppe nord/i }))
      expect(screen.getByRole('button', { name: 'Raum 2' })).toHaveAttribute('aria-pressed', 'false')

      fireEvent.click(screen.getByTestId('map-plane'), { clientX: 100, clientY: 50 })
      await waitFor(() => expect(maps.putConnectionPoint).toHaveBeenCalled())
      expect(maps.placeRoom).not.toHaveBeenCalled()
    })

    it('shows connections read-only to non-admins', async () => {
      renderPage('/maps/m1')
      await screen.findByRole('button', { name: /aufzug a \(aufzug\)/i })
      expect(screen.queryByRole('button', { name: /punkt setzen/i })).not.toBeInTheDocument()
      expect(screen.queryByLabelText(/name der verbindung/i)).not.toBeInTheDocument()
    })
  })
})

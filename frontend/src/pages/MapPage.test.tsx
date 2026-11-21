import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import MapPage from './MapPage'
import * as mapsApi from '../API/maps'
import { ApiError } from '../API/client'
import * as buildingsApi from '../API/buildings'
import * as floorsApi from '../API/floors'
import * as pathApi from '../API/pathNetwork'
import { sampleNetwork } from '../components/PathNetwork/fixtures'
import type { MapDetail, MapSummary } from '../types/map'

vi.mock('../API/maps')
vi.mock('../API/buildings')
vi.mock('../API/floors')
vi.mock('../API/pathNetwork')
vi.mock('../auth/useCurrentRoles', () => ({ useCurrentRoles: () => ({
  permissions: refreshingNow ? [] : rightsOverride ?? (editable ? ['READ', 'MAP_MANAGE', 'ROOM_PLACEMENT_MANAGE', 'CONNECTION_MANAGE'] : ['READ']),
  refreshing: refreshingNow,
  lastKnownPermissions: rightsOverride ?? (editable ? ['READ', 'MAP_MANAGE', 'ROOM_PLACEMENT_MANAGE', 'CONNECTION_MANAGE'] : ['READ']),
}) }))

const maps = vi.mocked(mapsApi)
// Administration mode and the administrator role are decided by the /admin route guard; the page only gets `editable`.
let editable = false
let rightsOverride: string[] | undefined
let refreshingNow = false

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
        <Route path="/maps" element={<MapPage editable={editable} />} />
        <Route path="/maps/:mapId" element={<MapPage editable={editable} />} />
        <Route path="/admin/maps" element={<MapPage editable={editable} />} />
        <Route path="/admin/maps/:mapId" element={<MapPage editable={editable} />} />
      </Routes>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  vi.resetAllMocks()
  editable = false
  rightsOverride = undefined
  refreshingNow = false
  maps.mapImageUrl.mockImplementation((m) => `/api/maps/${m.id}/image?v=${m.imageVersion}`)
  maps.listUnplacedRooms.mockResolvedValue([])
  maps.listConnections.mockResolvedValue([])
  vi.mocked(buildingsApi).listBuildings.mockResolvedValue([])
  vi.mocked(floorsApi).listFloors.mockResolvedValue([])
  vi.mocked(pathApi).getNetworkWide.mockResolvedValue({ maps: [sampleNetwork()], connectionGroups: [] })
})

describe('MapPage', () => {
  it('keeps room placements read-only for a connection manager', async () => {
    editable = true
    rightsOverride = ['READ', 'CONNECTION_MANAGE']
    maps.listMaps.mockResolvedValue([summary('m1', 'Haus A – EG')])
    maps.getMap.mockResolvedValue({ ...detail('m1', 'Haus A – EG'), placements: [
      { room: { id: 'r1', name: 'Raum 1', status: 'ACTIVE' }, x: 0.25, y: 0.25 },
    ] })
    renderPage('/admin/maps')
    await userEvent.click(await screen.findByRole('button', { name: 'Raum 1' }))
    expect(screen.queryByRole('button', { name: 'Platzierung entfernen' })).not.toBeInTheDocument()
    expect(maps.listUnplacedRooms).not.toHaveBeenCalled()
  })
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
    editable = true
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
    expect(maps.listUnplacedRooms).not.toHaveBeenCalled()
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
      editable = true
      renderPage()
      expect(await screen.findByRole('button', { name: 'Raum 1' })).toBeInTheDocument()
      const list = await screen.findByRole('list', { name: /nicht platzierte räume/i })
      expect(within(list).getByText('Raum 2')).toBeInTheDocument()
    })

    it('lets an admin select an unplaced room and place it by clicking the map', async () => {
      const user = userEvent.setup()
      editable = true
      maps.placeRoom.mockResolvedValue({ room: { id: 'r2', name: 'Raum 2', status: 'ACTIVE' }, x: 0.25, y: 0.25 })
      renderPage()

      await user.click(await screen.findByRole('button', { name: 'Raum 2' }))
      fireEvent.click(screen.getByTestId('map-plane'), { clientX: 50, clientY: 25 })

      await waitFor(() => expect(maps.placeRoom).toHaveBeenCalledWith('m1', 'r2', { x: 0.25, y: 0.25 }))
      await waitFor(() => expect(maps.getMap.mock.calls.length).toBeGreaterThan(1))
    })

    it('lets an admin remove the placement of the selected marker', async () => {
      const user = userEvent.setup()
      editable = true
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
      editable = true
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
      points: [{ id: 'p1', mapId: 'm1', x: 0.2, y: 0.3 }, { id: 'p2', mapId: 'm2', x: 0.6, y: 0.7 }],
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
      editable = true
      maps.putConnectionPoint.mockResolvedValue(stairs)
      renderPage('/maps/m1')

      await user.click(await screen.findByRole('button', { name: /punkt setzen: treppe nord/i }))
      fireEvent.click(screen.getByTestId('map-plane'), { clientX: 100, clientY: 50 })

      await waitFor(() => expect(maps.putConnectionPoint).toHaveBeenCalledWith('c2', 'm1', { x: 0.5, y: 0.5 }))
      expect(maps.placeRoom).not.toHaveBeenCalled()
    })

    it('selecting a connection clears a selected room and vice versa', async () => {
      const user = userEvent.setup()
      editable = true
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

describe('MapPage role re-check', () => {
  it('keeps the upload form mounted while roles are re-checked after the file dialog closes', async () => {
    editable = true
    maps.listMaps.mockResolvedValue([summary('m1', 'Haus A – EG')])
    maps.getMap.mockResolvedValue(detail('m1', 'Haus A – EG'))
    const view = renderPage('/admin/maps')
    const input = (await screen.findAllByLabelText(/grundriss/i))[0] as HTMLInputElement
    refreshingNow = true // window focus: permissions are momentarily empty
    window.dispatchEvent(new Event('focus'))
    view.rerender(
      <MemoryRouter initialEntries={['/admin/maps']}>
        <Routes><Route path="/admin/maps" element={<MapPage editable={editable} />} /></Routes>
      </MemoryRouter>,
    )
    expect(screen.getAllByLabelText(/grundriss/i)[0]).toBe(input)
  })
})

describe('MapPage path network', () => {
  const paths = vi.mocked(pathApi)

  function setupMap(withPlacements = false) {
    maps.listMaps.mockResolvedValue([summary('m1', 'Haus A – EG')])
    maps.getMap.mockResolvedValue({
      ...detail('m1', 'Haus A – EG'),
      placements: withPlacements ? [{ room: { id: 'r1', name: 'Raum 1', status: 'ACTIVE' }, x: 0.2, y: 0.2 }] : [],
      connections: withPlacements
        ? [{ id: 'c1', name: 'Aufzug A', type: 'ELEVATOR', incomplete: true, points: [{ id: 'p1', mapId: 'm1', x: 0.9, y: 0.8 }] }]
        : [],
    })
  }

  async function showNetwork() {
    await userEvent.click(await screen.findByLabelText(/wegenetz anzeigen/i))
    await screen.findByRole('button', { name: 'Wegpunkt 1' })
  }

  beforeEach(() => {
    vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockReturnValue({
      x: 0, y: 0, left: 0, top: 0, right: 200, bottom: 100, width: 200, height: 100, toJSON: () => ({}),
    })
    paths.addWaypoint.mockResolvedValue({ id: 'w9', mapId: 'm1', x: 0.25, y: 0.25 })
    paths.addSegment.mockResolvedValue({ id: 's9', mapId: 'm1', fromWaypointId: 'w1', toWaypointId: 'w3', lengthMeters: 1 })
    paths.putReferenceSegment.mockResolvedValue({ mapId: 'm1', x1: 0.2, y1: 0.8, x2: 0.6, y2: 0.8, lengthMeters: 12.5, pixelsPerMeter: 6.4 })
    paths.attachRoom.mockResolvedValue({ roomId: 'r1', mapId: 'm1', waypointId: 'w2' })
    paths.deleteWaypoint.mockResolvedValue(undefined)
  })

  it('loads nothing until the network is switched on, then shows it read-only to a viewer', async () => {
    setupMap()
    renderPage()
    await screen.findByLabelText(/wegenetz anzeigen/i)
    expect(paths.getNetworkWide).not.toHaveBeenCalled()
    await showNetwork()
    expect(paths.getNetworkWide).toHaveBeenCalledTimes(1)
    expect(screen.queryByRole('toolbar', { name: /werkzeuge für das wegenetz/i })).not.toBeInTheDocument()
    expect(screen.getByText(/referenzstrecke: 10 m/i)).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: /anbindung an das wegenetz/i })).not.toBeInTheDocument()
  })

  it('hides the editing tools from administrators without map management rights', async () => {
    editable = true
    rightsOverride = ['READ', 'ROOM_PLACEMENT_MANAGE', 'CONNECTION_MANAGE']
    setupMap()
    renderPage('/admin/maps')
    await showNetwork()
    expect(screen.queryByRole('toolbar', { name: /werkzeuge für das wegenetz/i })).not.toBeInTheDocument()
  })

  it('adds a waypoint where an editor clicks with the waypoint tool', async () => {
    editable = true
    setupMap()
    renderPage('/admin/maps')
    await showNetwork()
    await userEvent.click(screen.getByRole('button', { name: 'Wegpunkt setzen' }))
    fireEvent.click(screen.getByTestId('map-plane'), { clientX: 50, clientY: 25 })
    await waitFor(() => expect(paths.addWaypoint).toHaveBeenCalledWith('m1', { x: 0.25, y: 0.25 }))
    await waitFor(() => expect(paths.getNetworkWide).toHaveBeenCalledTimes(2))
  })

  it('connects two waypoints with two clicks', async () => {
    editable = true
    setupMap()
    renderPage('/admin/maps')
    await showNetwork()
    await userEvent.click(screen.getByRole('button', { name: 'Verbinden' }))
    await userEvent.click(screen.getByRole('button', { name: 'Wegpunkt 1' }))
    expect(screen.getByRole('button', { name: 'Wegpunkt 1 (Start der Verbindung)' })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Wegpunkt 3' }))
    await waitFor(() => expect(paths.addSegment).toHaveBeenCalledWith('m1', 'w1', 'w3'))
  })

  it('deletes a waypoint with the delete tool', async () => {
    editable = true
    setupMap()
    renderPage('/admin/maps')
    await showNetwork()
    await userEvent.click(screen.getByRole('button', { name: 'Löschen' }))
    await userEvent.click(screen.getByRole('button', { name: 'Wegpunkt 2' }))
    await waitFor(() => expect(paths.deleteWaypoint).toHaveBeenCalledWith('m1', 'w2'))
  })

  it('sets the scale from two clicked points and an entered length', async () => {
    editable = true
    setupMap()
    paths.getNetworkWide.mockResolvedValue({
      maps: [sampleNetwork({ referenceSegment: null, scale: { available: false, pixelsPerMeter: null, widthPx: 200, heightPx: 100 } })],
      connectionGroups: [],
    })
    renderPage('/admin/maps')
    await showNetwork()
    expect(screen.getByText(/maßstab fehlt/i, { selector: 'p' })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Referenzstrecke' }))
    fireEvent.click(screen.getByTestId('map-plane'), { clientX: 40, clientY: 80 })
    fireEvent.click(screen.getByTestId('map-plane'), { clientX: 120, clientY: 80 })
    await userEvent.type(await screen.findByLabelText(/reale länge in metern/i), '12,5')
    await userEvent.click(screen.getByRole('button', { name: 'Referenzstrecke speichern' }))
    await waitFor(() =>
      expect(paths.putReferenceSegment).toHaveBeenCalledWith('m1', { x1: 0.2, y1: 0.8, x2: 0.6, y2: 0.8, lengthMeters: 12.5 }),
    )
  })

  it('attaches a placed room to the waypoint that is clicked next', async () => {
    editable = true
    setupMap(true)
    renderPage('/admin/maps')
    await showNetwork()
    const panel = screen.getByRole('region', { name: /anbindung an das wegenetz/i })
    await userEvent.click(within(panel).getByRole('button', { name: 'Raum 1 anbinden' }))
    await userEvent.click(screen.getByRole('button', { name: 'Wegpunkt 2' }))
    await waitFor(() => expect(paths.attachRoom).toHaveBeenCalledWith('m1', 'r1', 'w2'))
  })

  it('lists the problems of the network for editors', async () => {
    editable = true
    setupMap(true)
    maps.listConnections.mockResolvedValue([
      { id: 'c1', name: 'Aufzug A', type: 'ELEVATOR', incomplete: true, points: [{ id: 'p1', mapId: 'm1', x: 0.9, y: 0.8 }] },
    ])
    paths.getNetworkWide.mockResolvedValue({
      maps: [sampleNetwork({ issues: { ...sampleNetwork().issues, unattachedRoomIds: ['r1'], unattachedConnectionPointIds: ['p1'] } })],
      connectionGroups: [{ connectionId: 'c1', name: 'Aufzug A', type: 'ELEVATOR', fullyAttached: false, points: [] }],
    })
    renderPage('/admin/maps')
    await showNetwork()
    expect(await screen.findByText(/raum 1 ist nicht angebunden/i)).toBeInTheDocument()
    expect(screen.getByText(/aufzug a ist nicht angebunden/i)).toBeInTheDocument()
    expect(screen.getAllByText(/nicht vollständig angebunden/i).length).toBeGreaterThan(0)
  })

  it('shows a failed edit as an error and keeps the page usable', async () => {
    editable = true
    setupMap()
    paths.addWaypoint.mockRejectedValue(new ApiError(409, { title: 'Conflict', status: 409, detail: 'An dieser Stelle liegt bereits ein Wegpunkt.' }))
    renderPage('/admin/maps')
    await showNetwork()
    await userEvent.click(screen.getByRole('button', { name: 'Wegpunkt setzen' }))
    fireEvent.click(screen.getByTestId('map-plane'), { clientX: 50, clientY: 25 })
    expect(await screen.findByText(/bereits ein wegpunkt/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Wegpunkt 1' })).toBeInTheDocument()
  })
})


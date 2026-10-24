import { render, waitFor, screen } from '@testing-library/react'
import axe from 'axe-core'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import HelpPage from './pages/HelpPage'
import RoomDetailPage from './pages/RoomDetailPage'
import AdminOccupancyPage from './pages/AdminOccupancyPage'
import UserRoleListPage from './pages/UserRoleListPage'
import { BuildingCatalog } from './components/BuildingCatalog/BuildingCatalog'
import { EquipmentCatalog } from './components/EquipmentCatalog/EquipmentCatalog'
import { ConnectionCatalog } from './components/ConnectionCatalog/ConnectionCatalog'
import * as roomsApi from './API/rooms'
import * as reservationsApi from './API/reservations'
import * as buildingsApi from './API/buildings'
import * as floorsApi from './API/floors'
import * as equipmentApi from './API/equipmentTypes'
import * as favoriteRoomsApi from './API/favoriteRooms'
import * as userRolesApi from './API/userRoles'
import * as occupancyApi from './API/adminOccupancy'
import type { Room } from './types/room'
import AppearanceOnboardingPage from './pages/AppearanceOnboardingPage'
import PersonalSettingsPage from './pages/PersonalSettingsPage'
import { AppearanceContext } from './auth/appearanceContext'

// Feature 020 (T035): structure checks with axe-core in jsdom. Colour contrast needs a real browser (visual review).
vi.mock('./API/rooms')
vi.mock('./API/reservations')
vi.mock('./API/roomStatus')
vi.mock('./API/roomDevices')
vi.mock('./API/favoriteRooms')
vi.mock('./API/buildings')
vi.mock('./API/floors')
vi.mock('./API/equipmentTypes')
vi.mock('./API/userRoles')
vi.mock('./API/adminOccupancy')
vi.mock('./auth/useAdminMode', () => ({
  useAdminMode: () => ({ loading: false, failed: false, admin: false, adminMode: false,
    permissions: ['READ', 'RESERVE', 'OWN_RESERVATION_MANAGE', 'STATISTICS_READ'], setMode: vi.fn() }),
}))
vi.mock('./auth/useAuth', () => ({ useAuth: () => ({ state: 'authenticated', user: { userId: 'u', displayName: 'Nutzer' } }) }))
vi.mock('./auth/useCurrentRoles', () => ({
  useCurrentRoles: () => ({ loading: false, failed: false, permissions: ['READ', 'STATISTICS_READ'] }),
}))

const RULES = [
  'label', 'list', 'listitem', 'th-has-data-cells', 'td-headers-attr', 'scope-attr-valid', 'table-fake-caption',
  'landmark-one-main', 'landmark-no-duplicate-main', 'landmark-no-duplicate-contentinfo', 'duplicate-id',
  'duplicate-id-aria', 'aria-valid-attr', 'aria-valid-attr-value', 'aria-required-attr', 'aria-roles',
  'button-name', 'link-name', 'image-alt', 'heading-order', 'empty-heading', 'form-field-multiple-labels',
]

async function expectNoViolations(container: HTMLElement) {
  const result = await axe.run(container, { runOnly: { type: 'rule', values: RULES } })
  expect(result.violations.map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(' ')).join(', ')}`)).toEqual([])
}

const room: Room = {
  id: 'room-1', name: 'Raum 2.14',
  building: { id: 'b1', name: 'Haus A', status: 'ACTIVE', hasElevator: true },
  floor: { id: 'f1', buildingId: 'b1', name: '2. OG', status: 'ACTIVE', groundFloor: false },
  status: 'ACTIVE', version: 0,
  seatingArrangements: [{ id: 's1', name: 'Reihen', maxCapacity: 40 }],
  equipmentTypeIds: ['e1'], notBarrierFree: false, barrierFreeReachable: true,
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(roomsApi.getRoom).mockResolvedValue(room)
  vi.mocked(favoriteRoomsApi.listFavoriteRooms).mockResolvedValue([])
  vi.mocked(reservationsApi.listRoomReservations).mockResolvedValue([])
  vi.mocked(reservationsApi.getAvailableEquipment).mockResolvedValue([])
  vi.mocked(equipmentApi.listEquipmentTypes).mockResolvedValue([{ id: 'e1', name: 'Beamer', status: 'ACTIVE' }])
  vi.mocked(buildingsApi.listBuildings).mockResolvedValue([{ id: 'b1', name: 'Haus A', status: 'ACTIVE', hasElevator: true }])
  vi.mocked(floorsApi.listFloors).mockResolvedValue([{ id: 'f1', buildingId: 'b1', name: '2. OG', status: 'ACTIVE', groundFloor: false }])
})

describe('accessibility structure (feature 020)', () => {
  it('first login theme choice and personal settings have named controls', async () => {
    const first = render(<AppearanceOnboardingPage onChoose={async () => undefined} onSkip={async () => undefined} />)
    expect(screen.getByRole('radio', { name: 'Hell' })).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: 'Dunkel' })).toBeInTheDocument()
    await expectNoViolations(first.container)
    first.unmount()

    const settings = render(<AppearanceContext.Provider value={{
      preference: { effectiveTheme: 'DARK', selectedTheme: 'DARK', onboardingPending: false },
      loading: false, error: null, retry: () => undefined,
      choose: async () => undefined, skip: async () => undefined,
    }}><PersonalSettingsPage /></AppearanceContext.Provider>)
    expect(screen.getByRole('radio', { name: 'Dunkel' })).toBeChecked()
    await expectNoViolations(settings.container)
  })

  it('detects a missing label (sanity check of the suite)', async () => {
    const { container } = render(<main id="inhalt"><input type="text" /></main>)
    const result = await axe.run(container, { runOnly: { type: 'rule', values: RULES } })
    expect(result.violations.map((v) => v.id)).toContain('label')
  })

  it('help page', async () => {
    const { container } = render(<MemoryRouter><HelpPage /></MemoryRouter>)
    await expectNoViolations(container)
  })

  it('room detail', async () => {
    const { container } = render(
      <MemoryRouter initialEntries={['/rooms/room-1']}>
        <Routes><Route path="/rooms/:roomId" element={<RoomDetailPage />} /></Routes>
      </MemoryRouter>,
    )
    await screen.findByRole('heading', { name: 'Raum auf einen Blick' })
    await screen.findByText('Beamer')
    await expectNoViolations(container)
  })

  it('building and equipment catalogs', async () => {
    const { container } = render(
      <main id="inhalt"><BuildingCatalog canManageBuildings canManageFloors /><EquipmentCatalog /></main>,
    )
    await screen.findByRole('list', { name: 'Gebäude' })
    await screen.findByRole('list', { name: 'Ausstattungstypen' })
    await expectNoViolations(container)
  })

  it('connection catalog', async () => {
    const { container } = render(
      <main id="inhalt">
        <ConnectionCatalog connections={[{ id: 'c1', name: 'Aufzug A', type: 'ELEVATOR', incomplete: false, points: [] }]}
          currentMapId="m0" admin activeConnectionId={undefined} onChanged={vi.fn()} onSelectForPlacement={vi.fn()} />
      </main>,
    )
    await expectNoViolations(container)
  })

  it('user list', async () => {
    vi.mocked(userRolesApi.listUsers).mockResolvedValue({
      items: [{ id: 'u1', displayName: 'Person', accountLabel: 'p@example.test' }], page: 0, size: 25, totalElements: 1,
    })
    const { container } = render(<MemoryRouter><UserRoleListPage /></MemoryRouter>)
    await screen.findByRole('list', { name: 'Benutzer' })
    await expectNoViolations(container)
  })

  it('administration occupancy', async () => {
    vi.mocked(occupancyApi.getOccupancySnapshot).mockResolvedValue({
      generatedAt: '2026-10-12T10:00:00Z', buildingId: null, totals: { checkedInPersons: 4, occupiedRooms: 1 },
      buildings: [{ buildingId: 'b1', buildingName: 'Haus A', buildingActive: true, checkedInPersons: 4, occupiedRooms: 1 }],
      runningBookings: [{
        reservationId: 'r1', roomId: 'room-1', roomName: 'Raum 2.14', buildingId: 'b1', buildingName: 'Haus A',
        floorId: 'f1', floorName: '2. OG', startTime: '2026-10-13T08:00:00Z', endTime: '2026-10-13T10:00:00Z',
        expectedAttendees: 4, checkInStatus: 'CHECKED_IN', checkedInAt: '2026-10-13T08:01:00Z',
      }],
    })
    vi.mocked(occupancyApi.getOccupancyForecast).mockResolvedValue({
      date: '2026-10-13', zone: 'Europe/Berlin', generatedAt: '2026-10-12T10:00:00Z', buildingId: null,
      peakExpectedPersons: null, buildings: [], hours: [],
    })
    const { container } = render(<MemoryRouter><AdminOccupancyPage /></MemoryRouter>)
    await waitFor(() => expect(screen.getByRole('list', { name: 'Laufende Buchungen' })).toBeInTheDocument())
    await expectNoViolations(container)
  })
})

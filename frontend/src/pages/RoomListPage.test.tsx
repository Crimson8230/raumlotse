import { MemoryRouter, useLocation } from 'react-router-dom'
import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import RoomListPage from './RoomListPage'
import * as roomsApi from '../API/rooms'
import * as buildingsApi from '../API/buildings'
import * as equipmentTypesApi from '../API/equipmentTypes'
import type { Room } from '../types/room'

vi.mock('../API/rooms')
vi.mock('../API/buildings')
vi.mock('../API/equipmentTypes')

const rooms = vi.mocked(roomsApi)
const buildings = vi.mocked(buildingsApi)
const equipmentTypes = vi.mocked(equipmentTypesApi)

function room(overrides: Partial<Room> = {}): Room {
  return {
    id: 'r1',
    name: 'Room 101',
    building: { id: 'b1', name: 'Main', status: 'ACTIVE', hasElevator: false },
    floor: { id: 'f1', buildingId: 'b1', name: '1', status: 'ACTIVE', groundFloor: false },
    status: 'ACTIVE',
    version: 0,
    seatingArrangements: [{ id: 's1', name: 'Theater', maxCapacity: 40 }],
    equipmentTypeIds: [],
    notBarrierFree: false,
    barrierFreeReachable: false,
    ...overrides,
  }
}

function LocationProbe() {
  return <output data-testid="location-search">{useLocation().search}</output>
}

function renderPage(url = '/rooms') {
  return render(
    <MemoryRouter initialEntries={[url]}>
      <RoomListPage />
      <LocationProbe />
    </MemoryRouter>,
  )
}

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((r) => {
    resolve = r
  })
  return { promise, resolve }
}

function lastSearchQuery(): string {
  const calls = rooms.searchRooms.mock.calls
  return calls[calls.length - 1][0].toString()
}

beforeEach(() => {
  vi.resetAllMocks()
  rooms.searchRooms.mockResolvedValue([])
  rooms.listRooms.mockResolvedValue([])
  rooms.listSeatingArrangementNames.mockResolvedValue(['Theater'])
  buildings.listBuildings.mockResolvedValue([{ id: 'b1', name: 'Main', status: 'ACTIVE', hasElevator: false }])
  equipmentTypes.listEquipmentTypes.mockResolvedValue([
    { id: 'e1', name: 'Projector', status: 'ACTIVE' },
    { id: 'e2', name: 'Old Screen', status: 'DEACTIVATED' },
  ])
})

describe('RoomListPage', () => {
  it('renders rooms with name, building, floor, and status', async () => {
    rooms.searchRooms.mockResolvedValue([room()])

    renderPage()

    await screen.findByText('Room 101')
    expect(screen.getByText('Main', { selector: 'span' })).toBeInTheDocument()
    expect(screen.getByText('1')).toBeInTheDocument()
    expect(screen.getByText('ACTIVE')).toBeInTheDocument()
  })

  it('renders the room status with a semantic status class', async () => {
    rooms.listRooms.mockResolvedValue([room(), room({ id: 'r2', name: 'Room 102', status: 'DEACTIVATED' })])
    const user = userEvent.setup()

    renderPage()
    await user.selectOptions(screen.getByLabelText(/status/i), 'all')

    await screen.findByText('Room 102')
    expect(screen.getByText('ACTIVE')).toHaveClass('status-active')
    expect(screen.getByText('DEACTIVATED')).toHaveClass('status-deactivated')
  })

  it('renders a styled empty state for a status without rooms', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.selectOptions(screen.getByLabelText(/status/i), 'deactivated')

    expect(await screen.findByText(/no rooms match/i)).toHaveClass('status-empty')
    expect(screen.queryByRole('list')).not.toBeInTheDocument()
  })

  it('searches active rooms with the criteria from the URL', async () => {
    renderPage('/rooms?minPersons=20')

    await waitFor(() => expect(rooms.searchRooms).toHaveBeenCalled())
    expect(lastSearchQuery()).toBe('minPersons=20')
    expect(rooms.listRooms).not.toHaveBeenCalled()
  })

  it('shows seating arrangements and equipment names in each result row', async () => {
    rooms.searchRooms.mockResolvedValue([
      room({
        seatingArrangements: [
          { id: 's1', name: 'Theater', maxCapacity: 60 },
          { id: 's2', name: 'U-Shape', maxCapacity: 20 },
        ],
        equipmentTypeIds: ['e1', 'e2'],
      }),
    ])

    renderPage()

    expect(await screen.findByRole('link', { name: 'Room 101' })).toHaveAttribute('href', '/rooms/r1')
    expect(screen.getByText('Theater (max 60), U-Shape (max 20)')).toBeInTheDocument()
    await screen.findByText('Projector, Old Screen')
  })

  it('updates the URL and searches again when the panel is submitted', async () => {
    const user = userEvent.setup()
    renderPage()
    await waitFor(() => expect(rooms.searchRooms).toHaveBeenCalledTimes(1))

    await user.type(screen.getByLabelText('Personen min.'), '20')
    await user.click(screen.getByRole('button', { name: 'Suchen' }))

    await waitFor(() => expect(screen.getByTestId('location-search')).toHaveTextContent('?minPersons=20'))
    await waitFor(() => expect(lastSearchQuery()).toBe('minPersons=20'))
  })

  it('shows a no-results message with a reset action that clears the URL', async () => {
    const user = userEvent.setup()
    renderPage('/rooms?minPersons=200')

    expect(await screen.findByText('Keine passenden Räume gefunden.')).toBeInTheDocument()
    const resetButtons = screen.getAllByRole('button', { name: 'Filter zurücksetzen' })
    await user.click(resetButtons[resetButtons.length - 1])

    await waitFor(() => expect(screen.getByTestId('location-search')).toHaveTextContent(''))
    await waitFor(() => expect(lastSearchQuery()).toBe(''))
  })

  it('uses the plain room list and disables the search for other statuses', async () => {
    const user = userEvent.setup()
    renderPage()
    await waitFor(() => expect(rooms.searchRooms).toHaveBeenCalled())

    await user.selectOptions(screen.getByLabelText(/status/i), 'deactivated')

    await waitFor(() => expect(rooms.listRooms).toHaveBeenCalledWith('deactivated'))
    expect(screen.getByText('Die Suche umfasst nur aktive Räume.')).toBeInTheDocument()
    expect(screen.getByLabelText('Personen min.')).toBeDisabled()
  })

  it('never lets an older, slower search response overwrite a newer one', async () => {
    const user = userEvent.setup()
    const slow = deferred<Room[]>()
    const fast = deferred<Room[]>()
    rooms.searchRooms.mockReturnValueOnce(slow.promise).mockReturnValueOnce(fast.promise)

    renderPage()
    await waitFor(() => expect(rooms.searchRooms).toHaveBeenCalledTimes(1))
    await user.type(screen.getByLabelText('Personen min.'), '20')
    await user.click(screen.getByRole('button', { name: 'Suchen' }))
    await waitFor(() => expect(rooms.searchRooms).toHaveBeenCalledTimes(2))

    await act(async () => fast.resolve([room({ id: 'new', name: 'Neuer Treffer' })]))
    await act(async () => slow.resolve([room({ id: 'old', name: 'Alter Treffer' })]))

    expect(await screen.findByText('Neuer Treffer')).toBeInTheDocument()
    expect(screen.queryByText('Alter Treffer')).not.toBeInTheDocument()
  })

  it('deactivates a room, hiding it from the active list on reload', async () => {
    const user = userEvent.setup()
    rooms.searchRooms.mockResolvedValueOnce([room()]).mockResolvedValueOnce([])
    rooms.deactivateRoom.mockResolvedValue(room({ status: 'DEACTIVATED' }))

    renderPage()
    await screen.findByText('Room 101')

    await user.click(screen.getByRole('button', { name: /deactivate room 101/i }))

    await waitFor(() => expect(rooms.deactivateRoom).toHaveBeenCalledWith('r1'))
    await waitFor(() => expect(rooms.searchRooms).toHaveBeenCalledTimes(2))
  })

  it('reactivates a deactivated room', async () => {
    const user = userEvent.setup()
    rooms.listRooms.mockResolvedValue([room({ status: 'DEACTIVATED' })])
    rooms.reactivateRoom.mockResolvedValue(room())

    renderPage()
    await user.selectOptions(screen.getByLabelText(/status/i), 'deactivated')
    await screen.findByText('Room 101')

    await user.click(screen.getByRole('button', { name: /reactivate room 101/i }))

    await waitFor(() => expect(rooms.reactivateRoom).toHaveBeenCalledWith('r1'))
  })

  it('deletes a room, removing it from the list', async () => {
    const user = userEvent.setup()
    rooms.searchRooms.mockResolvedValueOnce([room()]).mockResolvedValueOnce([])
    rooms.deleteRoom.mockResolvedValue(undefined)

    renderPage()
    await screen.findByText('Room 101')

    await user.click(screen.getByRole('button', { name: /delete room 101/i }))

    await waitFor(() => expect(rooms.deleteRoom).toHaveBeenCalledWith('r1'))
    await waitFor(() => expect(rooms.searchRooms).toHaveBeenCalledTimes(2))
  })

  it('displays error when deactivating a room blocked by active reservations', async () => {
    const user = userEvent.setup()
    rooms.searchRooms.mockResolvedValue([room()])
    rooms.deactivateRoom.mockRejectedValue(
      new Error('Room has active or upcoming reservations; cancel them first.'),
    )

    renderPage()
    await screen.findByText('Room 101')

    await user.click(screen.getByRole('button', { name: /deactivate room 101/i }))

    expect(
      await screen.findByText(/Room has active or upcoming reservations/i),
    ).toBeInTheDocument()
  })

  it('offers seating arrangement names and only active equipment types as filters', async () => {
    renderPage()

    expect(await screen.findByRole('option', { name: 'Theater' })).toBeInTheDocument()
    await screen.findByRole('checkbox', { name: 'Projector' })
    expect(screen.queryByRole('checkbox', { name: 'Old Screen' })).not.toBeInTheDocument()
    expect(equipmentTypes.listEquipmentTypes).toHaveBeenCalledWith('all')
  })

  it('passes repeated equipment filters from the URL to the search', async () => {
    renderPage('/rooms?equipmentTypeId=e1&equipmentTypeId=e2')

    await waitFor(() => expect(rooms.searchRooms).toHaveBeenCalled())
    expect(lastSearchQuery()).toBe('equipmentTypeId=e1&equipmentTypeId=e2')
  })

  it('marks barrier-free reachable rooms in the result list', async () => {
    rooms.searchRooms.mockResolvedValue([
      room({ id: 'r1', name: 'Erreichbar', barrierFreeReachable: true }),
      room({ id: 'r2', name: 'Nicht erreichbar' }),
    ])

    renderPage()

    const reachable = (await screen.findByText('Erreichbar')).closest('li')
    const unreachable = screen.getByText('Nicht erreichbar').closest('li')
    expect(reachable).toHaveTextContent('Barrierefrei erreichbar')
    expect(unreachable).not.toHaveTextContent('Barrierefrei erreichbar')
  })

  it('links results to the booking pre-fill when the search has a time window', async () => {
    rooms.searchRooms.mockResolvedValue([room()])

    renderPage('/rooms?date=2026-10-05&startTime=11%3A00&endTime=12%3A00')

    const link = new URL((await screen.findByRole('link', { name: 'Room 101' })).getAttribute('href') ?? '', 'http://localhost')
    expect(link.pathname).toBe('/rooms/r1')
    expect(link.searchParams.get('start')).toBe(new Date(2026, 9, 5, 11, 0).toISOString())
    expect(link.searchParams.get('end')).toBe(new Date(2026, 9, 5, 12, 0).toISOString())
    expect(lastSearchQuery()).toContain('from=')
  })

  it('links results without parameters when the search has no time window', async () => {
    rooms.searchRooms.mockResolvedValue([room()])

    renderPage()

    expect(await screen.findByRole('link', { name: 'Room 101' })).toHaveAttribute('href', '/rooms/r1')
  })

  it('offers a "Buchen" action for each active result', async () => {
    rooms.searchRooms.mockResolvedValue([room()])

    renderPage()

    const book = await screen.findByRole('link', { name: 'Room 101 buchen' })
    expect(book).toHaveTextContent('Buchen')
    expect(book).toHaveAttribute('href', '/rooms/r1?book=true')
  })

  it('carries the search window in the "Buchen" action', async () => {
    rooms.searchRooms.mockResolvedValue([room()])

    renderPage('/rooms?date=2026-10-05&startTime=11%3A00&endTime=12%3A00')

    const href = (await screen.findByRole('link', { name: 'Room 101 buchen' })).getAttribute('href') ?? ''
    const link = new URL(href, 'http://localhost')
    expect(link.searchParams.get('book')).toBe('true')
    expect(link.searchParams.get('start')).toBe(new Date(2026, 9, 5, 11, 0).toISOString())
  })

  it('offers no "Buchen" action for deactivated rooms', async () => {
    const user = userEvent.setup()
    rooms.listRooms.mockResolvedValue([room({ id: 'r2', name: 'Room 102', status: 'DEACTIVATED' })])

    renderPage()
    await user.selectOptions(screen.getByLabelText(/status/i), 'all')

    await screen.findByText('Room 102')
    expect(screen.queryByRole('link', { name: 'Room 102 buchen' })).not.toBeInTheDocument()
  })
})

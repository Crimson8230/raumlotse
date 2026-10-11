import { act, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { getOccupancyForecast, getOccupancySnapshot } from '../API/adminOccupancy'
import { listBuildings } from '../API/buildings'
import { ApiError } from '../API/client'
import { RequirePermission } from '../auth/RequirePermission'
import AdminOccupancyPage from './AdminOccupancyPage'
import type { BookingEntry, HourForecast, OccupancyForecast, OccupancySnapshot } from '../types/adminOccupancy'

vi.mock('../API/adminOccupancy')
vi.mock('../API/buildings')
let allowed = false
vi.mock('../auth/useCurrentRoles', () => ({ useCurrentRoles: () => ({
  loading: false, failed: false, permissions: allowed ? ['READ', 'STATISTICS_READ'] : ['READ'],
}) }))

function snapshot(overrides: Partial<OccupancySnapshot> = {}): OccupancySnapshot {
  return {
    generatedAt: '2026-10-12T10:00:00Z',
    buildingId: null,
    totals: { checkedInPersons: 14, occupiedRooms: 2 },
    buildings: [],
    runningBookings: [],
    ...overrides,
  }
}

function booking(overrides: Partial<BookingEntry> = {}): BookingEntry {
  return {
    reservationId: 'res-1', roomId: 'r2', roomName: 'R2', buildingId: 'a', buildingName: 'MCI III',
    floorId: 'a1', floorName: '1. OG', startTime: '2026-10-13T08:00:00Z', endTime: '2026-10-13T10:00:00Z',
    expectedAttendees: 12, checkInStatus: 'NOT_CHECKED_IN', checkedInAt: null, ...overrides,
  }
}

function hourOf(label: string, overrides: Partial<HourForecast> = {}): HourForecast {
  const utcHour = String(Number(label.slice(0, 2)) - 2).padStart(2, '0')
  return {
    start: `2026-10-13T${utcHour}:00:00Z`, end: `2026-10-13T${utcHour}:59:59Z`, localLabel: label, past: false,
    expectedPersons: 0, bookedRooms: 0, peak: false,
    buildings: [
      { buildingId: 'a', expectedPersons: 0, bookedRooms: 0, peak: false },
      { buildingId: 'b', expectedPersons: 0, bookedRooms: 0, peak: false },
    ],
    floors: [], ...overrides,
  }
}

function forecast(overrides: Partial<OccupancyForecast> = {}): OccupancyForecast {
  return {
    date: '2026-10-13', zone: 'Europe/Berlin', generatedAt: '2026-10-12T10:00:00Z', buildingId: null,
    peakExpectedPersons: null,
    buildings: [
      { buildingId: 'a', buildingName: 'MCI III', buildingActive: true, peakExpectedPersons: null },
      { buildingId: 'b', buildingName: 'MCI IV', buildingActive: true, peakExpectedPersons: null },
    ],
    hours: [hourOf('09:00'), hourOf('10:00')],
    ...overrides,
  }
}

function busyForecast(): OccupancyForecast {
  return forecast({
    peakExpectedPersons: 24,
    buildings: [
      { buildingId: 'a', buildingName: 'MCI III', buildingActive: true, peakExpectedPersons: 17 },
      { buildingId: 'b', buildingName: 'MCI IV', buildingActive: true, peakExpectedPersons: 7 },
    ],
    hours: [
      hourOf('09:00', { past: true, expectedPersons: null, bookedRooms: null, buildings: [
        { buildingId: 'a', expectedPersons: null, bookedRooms: null, peak: false },
        { buildingId: 'b', expectedPersons: null, bookedRooms: null, peak: false },
      ] }),
      hourOf('10:00', {
        expectedPersons: 24, bookedRooms: 3, peak: true,
        buildings: [
          { buildingId: 'a', expectedPersons: 17, bookedRooms: 2, peak: true },
          { buildingId: 'b', expectedPersons: 7, bookedRooms: 1, peak: true },
        ],
        floors: [
          { buildingId: 'a', buildingName: 'MCI III', floorId: 'a1', floorName: '1. OG', expectedPersons: 12,
            bookings: [booking({ checkInStatus: 'CHECKED_IN', checkedInAt: '2026-10-13T07:55:00Z' })] },
          { buildingId: 'b', buildingName: 'MCI IV', floorId: 'b0', floorName: 'EG', expectedPersons: 7,
            bookings: [booking({ reservationId: 'res-3', roomId: 'r3', roomName: 'R3', buildingId: 'b', buildingName: 'MCI IV',
              floorId: 'b0', floorName: 'EG', expectedAttendees: 7, endTime: '2026-10-13T09:00:00Z' })] },
          { buildingId: 'a', buildingName: 'MCI III', floorId: 'a0', floorName: 'EG', expectedPersons: 5,
            bookings: [booking({ reservationId: 'res-1b', roomId: 'r1', roomName: 'R1', floorId: 'a0', floorName: 'EG', expectedAttendees: 5 })] },
        ],
      }),
    ],
  })
}

function todayLocal(): string {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
}

function tile(name: string) {
  return screen.getByRole('article', { name })
}

beforeEach(() => {
  vi.resetAllMocks()
  allowed = false
  vi.mocked(getOccupancySnapshot).mockResolvedValue(snapshot())
  vi.mocked(listBuildings).mockResolvedValue([])
  vi.mocked(getOccupancyForecast).mockResolvedValue(forecast())
})

afterEach(() => {
  vi.useRealTimers()
})

describe('AdminOccupancyPage – current situation', () => {
  it('does not load occupancy for a direct route without STATISTICS_READ', () => {
    render(<MemoryRouter initialEntries={['/admin/occupancy']}><Routes>
      <Route element={<RequirePermission any={['STATISTICS_READ']} />}>
        <Route path="/admin/occupancy" element={<AdminOccupancyPage />} />
      </Route>
    </Routes></MemoryRouter>)
    expect(screen.getByRole('alert')).toHaveTextContent('nicht verfügbar')
    expect(getOccupancySnapshot).not.toHaveBeenCalled()
    expect(getOccupancyForecast).not.toHaveBeenCalled()
  })

  it('shows checked-in persons, occupied rooms and the data timestamp', async () => {
    render(<AdminOccupancyPage />)
    expect(await screen.findByRole('heading', { name: 'Belegung' })).toBeInTheDocument()
    expect(within(await screen.findByRole('article', { name: 'Eingecheckte Personen' })).getByText('14')).toBeInTheDocument()
    expect(within(tile('Belegte Räume')).getByText('2')).toBeInTheDocument()
    expect(screen.getByText(/erwartete Teilnehmende eingecheckter Buchungen/)).toBeInTheDocument()
    expect(screen.getByText(/^Stand:/)).toBeInTheDocument()
  })

  it('shows an empty state when no room is in use', async () => {
    vi.mocked(getOccupancySnapshot).mockResolvedValue(snapshot({ totals: { checkedInPersons: 0, occupiedRooms: 0 } }))
    render(<AdminOccupancyPage />)
    expect(await screen.findByText('Derzeit ist kein Raum in Nutzung.')).toBeInTheDocument()
    expect(within(tile('Eingecheckte Personen')).getByText('0')).toBeInTheDocument()
  })

  it('reloads every 30 seconds without a manual reload', async () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] })
    vi.mocked(getOccupancySnapshot)
      .mockResolvedValueOnce(snapshot())
      .mockResolvedValueOnce(snapshot({ generatedAt: '2026-10-12T10:00:30Z', totals: { checkedInPersons: 20, occupiedRooms: 3 } }))
    render(<AdminOccupancyPage />)
    expect(within(await screen.findByRole('article', { name: 'Eingecheckte Personen' })).getByText('14')).toBeInTheDocument()

    await act(async () => { vi.advanceTimersByTime(30_000) })

    expect(await within(tile('Eingecheckte Personen')).findByText('20')).toBeInTheDocument()
    expect(within(tile('Belegte Räume')).getByText('3')).toBeInTheDocument()
    expect(getOccupancySnapshot).toHaveBeenCalledTimes(2)
  })

  it('keeps the last values and warns that they may be stale when a reload fails', async () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] })
    vi.mocked(getOccupancySnapshot)
      .mockResolvedValueOnce(snapshot())
      .mockRejectedValueOnce(new ApiError(503))
    render(<AdminOccupancyPage />)
    expect(within(await screen.findByRole('article', { name: 'Eingecheckte Personen' })).getByText('14')).toBeInTheDocument()
    const stamp = screen.getByText(/^Stand:/).textContent

    await act(async () => { vi.advanceTimersByTime(30_000) })

    expect(await screen.findByText(/möglicherweise veraltet/)).toBeInTheDocument()
    expect(within(tile('Eingecheckte Personen')).getByText('14')).toBeInTheDocument()
    expect(screen.getByText(/^Stand:/).textContent).toBe(stamp)
  })

  it('removes all occupancy data when access is revoked while the page is open', async () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] })
    vi.mocked(getOccupancySnapshot)
      .mockResolvedValueOnce(snapshot())
      .mockRejectedValueOnce(new ApiError(403))
    render(<AdminOccupancyPage />)
    expect(within(await screen.findByRole('article', { name: 'Eingecheckte Personen' })).getByText('14')).toBeInTheDocument()

    await act(async () => { vi.advanceTimersByTime(30_000) })

    expect(await screen.findByRole('alert')).toHaveTextContent('nicht mehr erlaubt')
    expect(screen.queryByRole('article', { name: 'Eingecheckte Personen' })).not.toBeInTheDocument()
    expect(screen.queryByText(/^Stand:/)).not.toBeInTheDocument()
  })

  it('hides filter and forecast while access is denied and restores them once access is granted again', async () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] })
    vi.mocked(getOccupancySnapshot)
      .mockResolvedValueOnce(snapshot())
      .mockRejectedValueOnce(new ApiError(403))
      .mockResolvedValueOnce(snapshot({ totals: { checkedInPersons: 5, occupiedRooms: 1 } }))
    render(<AdminOccupancyPage />)
    expect(await screen.findByLabelText('Tag')).toBeInTheDocument()

    await act(async () => { vi.advanceTimersByTime(30_000) })

    expect(await screen.findByRole('alert')).toHaveTextContent('nicht mehr erlaubt')
    expect(screen.queryByLabelText('Gebäude')).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Tagesprognose' })).not.toBeInTheDocument()

    vi.mocked(getOccupancyForecast).mockClear()
    await act(async () => { vi.advanceTimersByTime(30_000) })

    expect(within(await screen.findByRole('article', { name: 'Eingecheckte Personen' })).getByText('5')).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Gebäude')).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: 'Tagesprognose' })).toBeInTheDocument()
    expect(getOccupancyForecast).toHaveBeenCalled()
  })
})

describe('AdminOccupancyPage – day forecast', () => {
  it('loads today by default and only allows today or later', async () => {
    render(<AdminOccupancyPage />)
    const input = await screen.findByLabelText('Tag')
    expect(input).toHaveAttribute('min', todayLocal())
    expect(input).toHaveValue(todayLocal())
    expect(getOccupancyForecast).toHaveBeenCalledWith(todayLocal(), undefined)
  })

  it('loads the chosen day', async () => {
    const user = userEvent.setup()
    render(<AdminOccupancyPage />)
    const input = await screen.findByLabelText('Tag')
    await user.clear(input)
    await user.type(input, '2099-01-05')
    expect(getOccupancyForecast).toHaveBeenLastCalledWith('2099-01-05', undefined)
  })

  it('shows planned values per hour, per building and in total with peaks', async () => {
    vi.mocked(getOccupancyForecast).mockResolvedValue(busyForecast())
    render(<AdminOccupancyPage />)
    const table = await screen.findByRole('table', { name: /Tagesprognose/ })
    expect(within(table).getByRole('columnheader', { name: 'MCI III' })).toBeInTheDocument()
    expect(within(table).getByRole('columnheader', { name: 'MCI IV' })).toBeInTheDocument()
    expect(within(table).getByRole('columnheader', { name: 'Gesamt' })).toBeInTheDocument()
    const ten = within(table).getByRole('row', { name: /10:00/ })
    expect(within(ten).getByText('17')).toBeInTheDocument()
    expect(within(ten).getByText('24')).toBeInTheDocument()
    expect(within(ten).getAllByText('Spitze')).toHaveLength(3)
    expect(within(within(table).getByRole('row', { name: /09:00/ })).getByText('vergangen')).toBeInTheDocument()
    expect(screen.getByText(/geplant/i)).toBeInTheDocument()
  })

  it('lists where people are expected in an hour, grouped by floor', async () => {
    const user = userEvent.setup()
    vi.mocked(getOccupancyForecast).mockResolvedValue(busyForecast())
    render(<AdminOccupancyPage />)
    await user.click(await screen.findByRole('button', { name: '10:00' }))

    const detail = screen.getByRole('dialog', { name: /10:00/ })
    expect(detail).toHaveAttribute('aria-modal', 'true')
    const groups = within(detail).getAllByRole('heading', { level: 3 }).map(h => h.textContent)
    expect(groups).toEqual(['MCI III · 1. OG – 12 Personen', 'MCI IV · EG – 7 Personen', 'MCI III · EG – 5 Personen'])
    expect(within(detail).getByText(/R2 · 10:00–12:00 · 12 Personen · eingecheckt/)).toBeInTheDocument()
    expect(within(detail).getByText(/R3 · 10:00–11:00 · 7 Personen · reserviert/)).toBeInTheDocument()

    await user.click(within(detail).getByRole('button', { name: 'Zur aktuellen Situation' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('opens the hour detail as a popup that closes with the button, Escape or the backdrop', async () => {
    const user = userEvent.setup()
    vi.mocked(getOccupancyForecast).mockResolvedValue(busyForecast())
    render(<AdminOccupancyPage />)
    const hourButton = await screen.findByRole('button', { name: '10:00' })

    await user.click(hourButton)
    const close = within(screen.getByRole('dialog', { name: /10:00/ })).getByRole('button', { name: 'Schließen' })
    expect(close).toHaveFocus()
    await user.click(close)
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(hourButton).toHaveFocus()

    await user.click(hourButton)
    await user.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()

    await user.click(hourButton)
    await user.click(screen.getByTestId('occupancy-dialog-backdrop'))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('offers no detail for past hours', async () => {
    vi.mocked(getOccupancyForecast).mockResolvedValue(busyForecast())
    render(<AdminOccupancyPage />)
    await screen.findByRole('button', { name: '10:00' })
    expect(screen.queryByRole('button', { name: '09:00' })).not.toBeInTheDocument()
  })

  it('shows a hint for a day without bookings', async () => {
    render(<AdminOccupancyPage />)
    expect(await screen.findByText('An diesem Tag sind keine Buchungen vorhanden.')).toBeInTheDocument()
    expect(screen.queryByText('Spitze')).not.toBeInTheDocument()
  })

  it('shows the message of a rejected forecast', async () => {
    vi.mocked(getOccupancyForecast).mockRejectedValue(
      new ApiError(400, { title: 'Bad Request', status: 400, detail: 'Vergangene Tage sind im Bereich Statistiken auswertbar.' }))
    render(<AdminOccupancyPage />)
    expect(await screen.findByText('Vergangene Tage sind im Bereich Statistiken auswertbar.')).toBeInTheDocument()
  })
})

describe('AdminOccupancyPage – running bookings', () => {
  it('lists running bookings with location, time, attendees and check-in status', async () => {
    vi.mocked(getOccupancySnapshot).mockResolvedValue(snapshot({ runningBookings: [
      booking({ checkInStatus: 'CHECKED_IN', checkedInAt: '2026-10-13T07:55:00Z' }),
      booking({ reservationId: 'res-3', roomName: 'R3', buildingName: 'MCI IV', floorName: 'EG', expectedAttendees: 6 }),
    ] }))
    render(<AdminOccupancyPage />)
    const list = await screen.findByRole('list', { name: 'Laufende Buchungen' })
    const items = within(list).getAllByRole('listitem')
    expect(items[0]).toHaveTextContent('MCI III · 1. OG · R2 · 10:00–12:00 · 12 Personen · eingecheckt seit 09:55')
    expect(items[1]).toHaveTextContent('MCI IV · EG · R3 · 10:00–12:00 · 6 Personen · noch nicht eingecheckt')
  })

  it('shows an empty state when no booking is running', async () => {
    render(<AdminOccupancyPage />)
    expect(await screen.findByText('Derzeit laufen keine Buchungen.')).toBeInTheDocument()
  })
})

describe('AdminOccupancyPage – buildings', () => {
  const buildings = [
    { id: 'a', name: 'MCI III', status: 'ACTIVE' as const, hasElevator: true },
    { id: 'b', name: 'MCI IV', status: 'ACTIVE' as const, hasElevator: false },
  ]

  it('shows persons and occupied rooms per building', async () => {
    vi.mocked(getOccupancySnapshot).mockResolvedValue(snapshot({ buildings: [
      { buildingId: 'a', buildingName: 'MCI III', buildingActive: true, checkedInPersons: 8, occupiedRooms: 1 },
      { buildingId: 'b', buildingName: 'MCI IV', buildingActive: true, checkedInPersons: 6, occupiedRooms: 2 },
    ] }))
    render(<AdminOccupancyPage />)
    const table = await screen.findByRole('table', { name: /je Gebäude/ })
    expect(within(within(table).getByRole('row', { name: /MCI III/ })).getByText('8')).toBeInTheDocument()
    expect(within(within(table).getByRole('row', { name: /MCI IV/ })).getByText('2')).toBeInTheDocument()
  })

  it('filters the current view and the forecast by building and can reset the filter', async () => {
    const user = userEvent.setup()
    vi.mocked(listBuildings).mockResolvedValue(buildings)
    render(<AdminOccupancyPage />)
    const select = await screen.findByLabelText('Gebäude')
    await screen.findByRole('option', { name: 'MCI IV' })
    expect(listBuildings).toHaveBeenCalledWith('active')

    await user.selectOptions(select, 'b')

    expect(getOccupancySnapshot).toHaveBeenLastCalledWith('b')
    expect(getOccupancyForecast).toHaveBeenLastCalledWith(todayLocal(), 'b')
    expect(screen.getByText('Gefiltert auf: MCI IV')).toBeInTheDocument()

    await user.selectOptions(select, '')
    expect(getOccupancySnapshot).toHaveBeenLastCalledWith(undefined)
    expect(screen.queryByText(/Gefiltert auf/)).not.toBeInTheDocument()
  })

  it('keeps the building filter for the 30-second refresh', async () => {
    const user = userEvent.setup()
    vi.mocked(listBuildings).mockResolvedValue(buildings)
    render(<AdminOccupancyPage />)
    await screen.findByRole('option', { name: 'MCI III' })
    await user.selectOptions(screen.getByLabelText('Gebäude'), 'a')
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] })
    vi.mocked(getOccupancySnapshot).mockClear()
    // Re-select so the interval is created with fake timers.
    await act(async () => { await user.selectOptions(screen.getByLabelText('Gebäude'), 'b') })
    await act(async () => { vi.advanceTimersByTime(30_000) })
    expect(vi.mocked(getOccupancySnapshot).mock.calls.every(call => call[0] === 'b')).toBe(true)
    expect(getOccupancySnapshot).toHaveBeenCalledTimes(2)
  })
})

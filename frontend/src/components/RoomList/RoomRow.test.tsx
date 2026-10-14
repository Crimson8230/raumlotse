import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as reservationsApi from '../../API/reservations'
import type { Room, RoomSearchFormState } from '../../types/room'
import { resetDayWindowsCache } from './dayWindows'
import { RoomRow } from './RoomRow'

vi.mock('../../API/reservations')
vi.mock('../../API/favoriteRooms')
const reservations = vi.mocked(reservationsApi)

const emptySearch: RoomSearchFormState = {
  date: '', startTime: '', endTime: '', minCapacity: '', seatingArrangement: '', equipmentTypeIds: [],
  barrierFree: false, buildingId: '', favoritesOnly: false,
} as unknown as RoomSearchFormState

function room(overrides: Partial<Room> = {}): Room {
  return {
    id: 'r1',
    name: 'Raum 2.14',
    building: { id: 'b1', name: 'Haus A', status: 'ACTIVE', hasElevator: true },
    floor: { id: 'f1', buildingId: 'b1', name: '2. OG', status: 'ACTIVE', groundFloor: false },
    status: 'ACTIVE',
    version: 0,
    seatingArrangements: [{ id: 's1', name: 'Reihen', maxCapacity: 40 }],
    equipmentTypeIds: [],
    notBarrierFree: false,
    barrierFreeReachable: true,
    ...overrides,
  }
}

const handlers = { onFavoriteChanged: vi.fn(), onDeactivate: vi.fn(), onReactivate: vi.fn(), onDelete: vi.fn() }

function renderRow(props: Partial<Parameters<typeof RoomRow>[0]> = {}) {
  return render(
    <MemoryRouter>
      <ul>
        <RoomRow
          room={room()}
          index={0}
          now={new Date(2026, 9, 14, 10, 0)}
          isFavorite={false}
          searchState={emptySearch}
          canReserve
          roomManage={false}
          equipmentNames={{}}
          {...handlers}
          {...props}
        />
      </ul>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  vi.resetAllMocks()
  resetDayWindowsCache()
  reservations.listRoomReservations.mockResolvedValue([])
})

describe('RoomRow', () => {
  it('shows name (serif class), building, floor and the day strip', async () => {
    renderRow()
    const link = screen.getByRole('link', { name: 'Raum 2.14' })
    expect(link).toHaveClass('room-name')
    expect(screen.getByText('Haus A')).toBeInTheDocument()
    expect(screen.getByText('2. OG')).toBeInTheDocument()
    expect(await screen.findByRole('img', { name: /ganztägig frei/ })).toBeInTheDocument()
  })

  it('says "lädt" in words while the strip data is on its way', () => {
    reservations.listRoomReservations.mockReturnValue(new Promise(() => {}))
    renderRow()
    expect(screen.getByText('Belegung lädt …')).toBeInTheDocument()
  })

  it('shows "Buchen" as the single primary action for users who may reserve', async () => {
    renderRow()
    const book = screen.getByRole('link', { name: 'Raum 2.14 buchen' })
    expect(book).toHaveClass('btn-primary')
    await screen.findByRole('img')
    expect(document.querySelectorAll('.btn-primary')).toHaveLength(1)
  })

  it('offers "Öffnen" instead of "Buchen" without the reserve right', () => {
    renderRow({ canReserve: false })
    expect(screen.queryByRole('link', { name: /buchen/ })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Raum 2.14 öffnen' })).toBeInTheDocument()
    expect(document.querySelectorAll('.btn-primary')).toHaveLength(0)
  })

  it('does not count the favorite toggle as an action', () => {
    renderRow()
    expect(screen.getByRole('button', { name: /Favorit/ })).toBeInTheDocument()
    expect(document.querySelectorAll('.btn-primary')).toHaveLength(1)
  })

  it('groups management actions as secondary under "Verwalten"', async () => {
    const user = userEvent.setup()
    renderRow({ roomManage: true })
    const group = screen.getByRole('group', { name: 'Verwalten' })
    const deactivate = within(group).getByRole('button', { name: 'Raum 2.14 deaktivieren' })
    expect(group.querySelector('.btn-primary')).toBeNull()
    await user.click(deactivate)
    expect(handlers.onDeactivate).toHaveBeenCalled()
    await user.click(within(group).getByRole('button', { name: 'Raum 2.14 löschen' }))
    expect(handlers.onDelete).toHaveBeenCalled()
  })

  it('offers only management actions for an inactive room', () => {
    renderRow({ room: room({ status: 'DEACTIVATED' }), roomManage: true })
    expect(screen.queryByRole('link', { name: /buchen|öffnen/ })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Raum 2.14 reaktivieren' })).toBeInTheDocument()
    expect(screen.queryByRole('img')).not.toBeInTheDocument()
  })

  it('does not load strips beyond the first rows when rows cannot be observed', () => {
    renderRow({ index: 25 })
    expect(reservations.listRoomReservations).not.toHaveBeenCalled()
  })
})

describe('RoomRow styles (narrow screens, T023a)', () => {
  it('stacks info, strip and actions at 760 px and wraps long names', async () => {
    const { readFileSync } = await import('node:fs')
    const { join } = await import('node:path')
    const css = readFileSync(join(__dirname, 'RoomRow.css'), 'utf8')
    expect(css).toMatch(/@media \(max-width: 760px\)[\s\S]*grid-template-columns:\s*minmax\(0, 1fr\)/)
    expect(css).toMatch(/\.room-row__info[\s\S]*overflow-wrap:\s*anywhere/)
    expect(css).not.toMatch(/#[0-9a-fA-F]{3,6}\b/)
  })
})

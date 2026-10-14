import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Reservation } from '../../types/reservation'
import * as reservationsApi from '../../API/reservations'
import {
  MAX_CONCURRENT_QUERIES,
  dayRange,
  loadDayWindows,
  resetDayWindowsCache,
  toStripWindows,
} from './dayWindows'

vi.mock('../../API/reservations')
const api = vi.mocked(reservationsApi)

function reservation(overrides: Partial<Reservation>): Reservation {
  return {
    id: 'x',
    roomId: 'r1',
    roomName: 'Room',
    startTime: '2026-10-14T08:00:00.000Z',
    endTime: '2026-10-14T09:00:00.000Z',
    status: 'RESERVED',
    seatingArrangement: null,
    expectedAttendees: null,
    additionalEquipment: [],
    createdBy: null,
    reservedFor: null,
    createdAt: null,
    ownedByMe: false,
    ...overrides,
  }
}

beforeEach(() => {
  vi.resetAllMocks()
  resetDayWindowsCache()
})

afterEach(() => {
  vi.useRealTimers()
})

describe('dayRange', () => {
  it('spans 07:00 to 20:00 local time of the given day', () => {
    const { start, end } = dayRange(new Date(2026, 9, 14, 12, 30))
    expect([start.getHours(), start.getMinutes(), start.getDate()]).toEqual([7, 0, 14])
    expect([end.getHours(), end.getMinutes(), end.getDate()]).toEqual([20, 0, 14])
  })
})

describe('toStripWindows', () => {
  it('marks own reservations "meine" and others "belegt"', () => {
    const windows = toStripWindows([
      reservation({ id: 'a', ownedByMe: true }),
      reservation({ id: 'b', ownedByMe: false }),
    ])
    expect(windows.map((w) => w.state)).toEqual(['meine', 'belegt'])
  })

  it('drops cancelled and expired reservations and keeps completed ones as "belegt"', () => {
    const windows = toStripWindows([
      reservation({ status: 'CANCELLED' }),
      reservation({ status: 'EXPIRED' }),
      reservation({ status: 'COMPLETED' }),
      reservation({ status: 'ACTIVE' }),
    ])
    expect(windows.map((w) => w.state)).toEqual(['belegt', 'belegt'])
  })
})

describe('loadDayWindows', () => {
  const now = new Date(2026, 9, 14, 12, 0)

  it('queries the room for exactly the strip day', async () => {
    api.listRoomReservations.mockResolvedValue([])
    await loadDayWindows('r1', now)
    const { start, end } = dayRange(now)
    expect(api.listRoomReservations).toHaveBeenCalledWith('r1', start.toISOString(), end.toISOString())
  })

  it('reuses the answer for the same room and day', async () => {
    api.listRoomReservations.mockResolvedValue([reservation({})])
    await loadDayWindows('r1', now)
    await loadDayWindows('r1', now)
    expect(api.listRoomReservations).toHaveBeenCalledTimes(1)
  })

  it('queries again after the refresh interval', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(now)
    api.listRoomReservations.mockResolvedValue([])
    await loadDayWindows('r1', now)
    vi.setSystemTime(new Date(now.getTime() + 5 * 60 * 1000 + 1))
    await loadDayWindows('r1', new Date(Date.now()))
    expect(api.listRoomReservations).toHaveBeenCalledTimes(2)
  })

  it('never has more than the allowed number of queries in flight', async () => {
    let inFlight = 0
    let peak = 0
    const releases: Array<() => void> = []
    api.listRoomReservations.mockImplementation(
      () =>
        new Promise((resolve) => {
          inFlight += 1
          peak = Math.max(peak, inFlight)
          releases.push(() => {
            inFlight -= 1
            resolve([])
          })
        }),
    )
    const all = Array.from({ length: 10 }, (_, i) => loadDayWindows(`r${i}`, now))
    await Promise.resolve()
    expect(inFlight).toBe(MAX_CONCURRENT_QUERIES)
    while (releases.length > 0) {
      releases.shift()!()
      await Promise.resolve()
      await Promise.resolve()
    }
    await Promise.all(all)
    expect(peak).toBe(MAX_CONCURRENT_QUERIES)
  })

  it('does not cache a failed query', async () => {
    api.listRoomReservations.mockRejectedValueOnce(new Error('boom')).mockResolvedValueOnce([])
    await expect(loadDayWindows('r1', now)).rejects.toThrow('boom')
    await expect(loadDayWindows('r1', now)).resolves.toEqual([])
  })
})

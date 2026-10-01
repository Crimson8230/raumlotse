import { describe, expect, it } from 'vitest'
import type { Reservation } from '../../types/reservation'
import {
  deriveRoomDisplayStatus,
  getNotePreview,
  selectNextReservation,
  selectCurrentReservation,
} from './roomDisplayLogic'

const now = new Date('2026-09-20T10:00:00.000Z')

function reservationFixture(overrides: Partial<Reservation> = {}): Reservation {
  return {
    id: 'res-1',
    roomId: 'room-1',
    roomName: 'Room 101',
    startTime: '2026-09-20T09:00:00.000Z',
    endTime: '2026-09-20T11:00:00.000Z',
    status: 'ACTIVE',
    seatingArrangement: { id: 'seat-1', name: 'Theater', maxCapacity: 40 },
    expectedAttendees: 20,
    additionalEquipment: [],
    note: 'Current reservation',
    createdBy: 'Alice',
    reservedFor: 'Team Alpha',
    createdAt: '2026-09-19T09:00:00.000Z',
    ...overrides,
  }
}

describe('selectCurrentReservation', () => {
  it('selects a reservation at its inclusive start boundary', () => {
    const reservation = reservationFixture({
      startTime: '2026-09-20T10:00:00.000Z',
      endTime: '2026-09-20T11:00:00.000Z',
    })

    expect(selectCurrentReservation([reservation], now, 'room-1')).toBe(reservation)
  })

  it('excludes a reservation at its exclusive end boundary', () => {
    const reservation = reservationFixture({
      startTime: '2026-09-20T09:00:00.000Z',
      endTime: '2026-09-20T10:00:00.000Z',
    })

    expect(selectCurrentReservation([reservation], now, 'room-1')).toBeNull()
  })

  it('accepts only reservations for the displayed room in RESERVED or ACTIVE status', () => {
    const wrongRoom = reservationFixture({ id: 'res-wrong', roomId: 'room-2' })
    const cancelled = reservationFixture({ id: 'res-cancelled', status: 'CANCELLED' })
    const reserved = reservationFixture({ id: 'res-reserved', status: 'RESERVED' })

    expect(selectCurrentReservation([wrongRoom, cancelled, reserved], now, 'room-1')).toBe(
      reserved,
    )
  })

  it('chooses the earliest start time and then the smallest id deterministically', () => {
    const later = reservationFixture({ id: 'res-z', startTime: '2026-09-20T09:30:00.000Z' })
    const sameStartHigherId = reservationFixture({
      id: 'res-b',
      startTime: '2026-09-20T09:00:00.000Z',
    })
    const sameStartLowerId = reservationFixture({
      id: 'res-a',
      startTime: '2026-09-20T09:00:00.000Z',
    })

    expect(
      selectCurrentReservation([later, sameStartHigherId, sameStartLowerId], now, 'room-1'),
    ).toBe(sameStartLowerId)
  })

  it('returns no reservation for ended, future, terminal, or wrong-room records', () => {
    const ended = reservationFixture({ id: 'ended', endTime: '2026-09-20T09:59:00.000Z' })
    const future = reservationFixture({
      id: 'future',
      startTime: '2026-09-20T10:01:00.000Z',
    })
    const completed = reservationFixture({ id: 'completed', status: 'COMPLETED' })
    const expired = reservationFixture({ id: 'expired', status: 'EXPIRED' })
    const cancelled = reservationFixture({ id: 'cancelled', status: 'CANCELLED' })
    const wrongRoom = reservationFixture({ id: 'wrong-room', roomId: 'room-2' })

    expect(
      selectCurrentReservation(
        [ended, future, completed, expired, cancelled, wrongRoom],
        now,
        'room-1',
      ),
    ).toBeNull()
  })
})

describe('deriveRoomDisplayStatus', () => {
  it('derives AVAILABLE when no current or future reservation exists', () => {
    expect(deriveRoomDisplayStatus([], now, 'room-1')).toBe('AVAILABLE')
  })

  it('derives RESERVED for a future RESERVED reservation', () => {
    const future = reservationFixture({
      id: 'future',
      status: 'RESERVED',
      startTime: '2026-09-20T11:00:00.000Z',
      endTime: '2026-09-20T12:00:00.000Z',
    })

    expect(deriveRoomDisplayStatus([future], now, 'room-1')).toBe('RESERVED')
  })

  it('gives current ACTIVE occupancy precedence over future RESERVED bookings', () => {
    const active = reservationFixture({ id: 'active', status: 'ACTIVE' })
    const future = reservationFixture({
      id: 'future',
      status: 'RESERVED',
      startTime: '2026-09-20T11:00:00.000Z',
      endTime: '2026-09-20T12:00:00.000Z',
    })

    expect(deriveRoomDisplayStatus([future, active], now, 'room-1')).toBe('OCCUPIED')
  })

  it('excludes terminal, wrong-room, and already ended records', () => {
    const records = [
      reservationFixture({ id: 'completed', status: 'COMPLETED' }),
      reservationFixture({ id: 'cancelled', status: 'CANCELLED' }),
      reservationFixture({ id: 'expired', status: 'EXPIRED' }),
      reservationFixture({ id: 'wrong-room', roomId: 'room-2', status: 'ACTIVE' }),
      reservationFixture({ id: 'ended', endTime: '2026-09-20T10:00:00.000Z' }),
    ]

    expect(deriveRoomDisplayStatus(records, now, 'room-1')).toBe('AVAILABLE')
  })
})

describe('selectNextReservation', () => {
  it('selects the earliest future RESERVED reservation for the room', () => {
    const later = reservationFixture({
      id: 'later',
      status: 'RESERVED',
      startTime: '2026-09-20T12:00:00.000Z',
      endTime: '2026-09-20T13:00:00.000Z',
    })
    const earliest = reservationFixture({
      id: 'earliest',
      status: 'RESERVED',
      startTime: '2026-09-20T11:00:00.000Z',
      endTime: '2026-09-20T12:00:00.000Z',
    })

    expect(selectNextReservation([later, earliest], now, 'room-1')).toBe(earliest)
  })

  it('uses the smallest id for equal start times and excludes non-RESERVED records', () => {
    const sameStartHigherId = reservationFixture({
      id: 'res-b',
      status: 'RESERVED',
      startTime: '2026-09-20T11:00:00.000Z',
      endTime: '2026-09-20T12:00:00.000Z',
    })
    const sameStartLowerId = reservationFixture({
      id: 'res-a',
      status: 'RESERVED',
      startTime: '2026-09-20T11:00:00.000Z',
      endTime: '2026-09-20T12:00:00.000Z',
    })
    const active = reservationFixture({
      id: 'active',
      status: 'ACTIVE',
      startTime: '2026-09-20T11:30:00.000Z',
      endTime: '2026-09-20T12:30:00.000Z',
    })

    expect(selectNextReservation([active, sameStartHigherId, sameStartLowerId], now, 'room-1')).toBe(
      sameStartLowerId,
    )
  })

  it('returns null when no future RESERVED reservation exists', () => {
    const current = reservationFixture({ status: 'ACTIVE' })
    const cancelled = reservationFixture({ id: 'cancelled', status: 'CANCELLED' })

    expect(selectNextReservation([current, cancelled], now, 'room-1')).toBeNull()
  })
})

describe('getNotePreview', () => {
  it('uses a visible fallback for an empty note', () => {
    expect(getNotePreview('   ')).toEqual({ text: 'No note provided', truncated: false })
    expect(getNotePreview(null)).toEqual({ text: 'No note provided', truncated: false })
  })

  it('preserves a short note without truncation', () => {
    expect(getNotePreview('Setup\nwith special characters: ä & ü')).toEqual({
      text: 'Setup\nwith special characters: ä & ü',
      truncated: false,
    })
  })

  it('adds an explicit marker when a long note exceeds the display limit', () => {
    const note = 'A'.repeat(300)
    const preview = getNotePreview(note, 40)

    expect(preview.truncated).toBe(true)
    expect(preview.text).toHaveLength(40)
    expect(preview.text.endsWith('...')).toBe(true)
    expect(preview.text.startsWith('A'.repeat(37))).toBe(true)
  })
})

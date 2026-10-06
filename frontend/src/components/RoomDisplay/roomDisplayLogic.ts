import type { Reservation } from '../../types/reservation'
import type { RoomDisplayStatus } from './roomDisplayTypes'

const ACTIVE_STATUSES = new Set<Reservation['status']>(['RESERVED', 'ACTIVE'])

export interface NotePreview {
  text: string
  truncated: boolean
}

export function getNotePreview(note: string | null | undefined, maxLength = 280): NotePreview {
  if (!note || note.trim().length === 0) {
    return { text: 'Keine Notiz vorhanden', truncated: false }
  }

  if (note.length <= maxLength) {
    return { text: note, truncated: false }
  }

  return {
    text: `${note.slice(0, Math.max(1, maxLength - 3)).trimEnd()}...`,
    truncated: true,
  }
}

export function selectCurrentReservation(
  reservations: Reservation[],
  currentDateTime: Date,
  roomId: string,
): Reservation | null {
  const currentTimestamp = currentDateTime.getTime()

  return reservations
    .filter((reservation) => {
      const startTimestamp = Date.parse(reservation.startTime)
      const endTimestamp = Date.parse(reservation.endTime)
      return (
        reservation.roomId === roomId &&
        ACTIVE_STATUSES.has(reservation.status) &&
        startTimestamp <= currentTimestamp &&
        currentTimestamp < endTimestamp
      )
    })
    .sort((left, right) => {
      const startDifference = Date.parse(left.startTime) - Date.parse(right.startTime)
      return startDifference || left.id.localeCompare(right.id)
    })[0] ?? null
}

export function deriveRoomDisplayStatus(
  reservations: Reservation[],
  currentDateTime: Date,
  roomId: string,
): RoomDisplayStatus {
  const currentTimestamp = currentDateTime.getTime()
  const roomReservations = reservations.filter((reservation) => reservation.roomId === roomId)
  const currentActive = roomReservations.some((reservation) => {
    return (
      reservation.status === 'ACTIVE' &&
      Date.parse(reservation.startTime) <= currentTimestamp &&
      currentTimestamp < Date.parse(reservation.endTime)
    )
  })

  if (currentActive) {
    return 'OCCUPIED'
  }

  const hasReservedBooking = roomReservations.some((reservation) => {
    const startTimestamp = Date.parse(reservation.startTime)
    const endTimestamp = Date.parse(reservation.endTime)
    return (
      reservation.status === 'RESERVED' &&
      (startTimestamp > currentTimestamp ||
        (startTimestamp <= currentTimestamp && currentTimestamp < endTimestamp))
    )
  })

  return hasReservedBooking ? 'RESERVED' : 'AVAILABLE'
}

export function selectNextReservation(
  reservations: Reservation[],
  currentDateTime: Date,
  roomId: string,
): Reservation | null {
  const currentTimestamp = currentDateTime.getTime()

  return reservations
    .filter((reservation) => {
      return (
        reservation.roomId === roomId &&
        reservation.status === 'RESERVED' &&
        Date.parse(reservation.startTime) > currentTimestamp
      )
    })
    .sort((left, right) => {
      const startDifference = Date.parse(left.startTime) - Date.parse(right.startTime)
      return startDifference || left.id.localeCompare(right.id)
    })[0] ?? null
}

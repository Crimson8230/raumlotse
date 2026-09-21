import type { Reservation } from '../../types/reservation'

const ACTIVE_STATUSES = new Set<Reservation['status']>(['RESERVED', 'ACTIVE'])

export interface NotePreview {
  text: string
  truncated: boolean
}

export function getNotePreview(note: string | null | undefined, maxLength = 280): NotePreview {
  if (!note || note.trim().length === 0) {
    return { text: 'No note provided', truncated: false }
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

function localDateKey(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export function selectUpcomingReservation(
  reservations: Reservation[],
  currentDateTime: Date,
  roomId: string,
): Reservation | null {
  const currentTimestamp = currentDateTime.getTime()
  const currentLocalDate = localDateKey(currentDateTime)

  return reservations
    .filter((reservation) => {
      const startTimestamp = Date.parse(reservation.startTime)
      const endTimestamp = Date.parse(reservation.endTime)
      const startDate = new Date(startTimestamp)

      return (
        reservation.roomId === roomId &&
        ACTIVE_STATUSES.has(reservation.status) &&
        Number.isFinite(startTimestamp) &&
        Number.isFinite(endTimestamp) &&
        startTimestamp > currentTimestamp &&
        endTimestamp > startTimestamp &&
        localDateKey(startDate) === currentLocalDate
      )
    })
    .sort((left, right) => {
      const startDifference = Date.parse(left.startTime) - Date.parse(right.startTime)
      return startDifference || left.id.localeCompare(right.id)
    })[0] ?? null
}

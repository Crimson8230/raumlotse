import type { Reservation } from '../../types/reservation'

export type RoomDisplayState = 'loading' | 'unavailable' | 'no-reservation' | 'reservation'

export interface RoomDisplayViewModel {
  roomName: string
  currentDateTime: Date
  state: RoomDisplayState
  reservation: Reservation | null
  upcomingReservation: Reservation | null
  errorMessage?: string
}

export interface RoomDisplayProps {
  roomName: string
  currentDateTime: Date
  reservation: Reservation | null
  upcomingReservation?: Reservation | null
  state: Exclude<RoomDisplayState, 'loading'>
  errorMessage?: string
}

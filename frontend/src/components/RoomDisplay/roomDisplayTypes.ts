import type { Reservation } from '../../types/reservation'

export type RoomDisplayStatus = 'AVAILABLE' | 'RESERVED' | 'OCCUPIED' | 'UNAVAILABLE'

export type RoomDisplayState = 'loading' | 'unavailable' | 'no-reservation' | 'reservation'

export interface RoomDisplayViewModel {
  roomName: string
  currentDateTime: Date
  state: RoomDisplayState
  status: RoomDisplayStatus
  reservation: Reservation | null
  nextReservation: Reservation | null
  errorMessage?: string
}

export interface RoomDisplayProps {
  roomName: string
  currentDateTime: Date
  reservation: Reservation | null
  status: RoomDisplayStatus
  nextReservation: Reservation | null
  state: Exclude<RoomDisplayState, 'loading'>
  errorMessage?: string
}

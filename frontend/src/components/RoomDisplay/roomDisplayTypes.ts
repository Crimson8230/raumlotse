import type { ReactNode } from 'react'
import type { DeviceStates } from '../../types/checkIn'
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
  /** On-site check-in link encoded as QR code (feature 014). */
  checkInLink?: string
  /** Simulated device states from the room status (feature 014). */
  devices?: DeviceStates | null
  /** Administration-only controls such as the simulated motion sensor (feature 014). */
  adminControls?: ReactNode
  /** Navigation shown centred below the display, e.g. back to the room page. */
  footer?: ReactNode
}

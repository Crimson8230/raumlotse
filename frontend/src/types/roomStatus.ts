import type { DeviceStates } from './checkIn'

export interface RoomStatus {
  roomId: string
  status: 'AVAILABLE' | 'RESERVED' | 'OCCUPIED'
  devices: DeviceStates
  /** Latest detected presence of the booking in use; only filled for administrators. */
  lastPresenceAt: string | null
}

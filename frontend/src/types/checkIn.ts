import type { RoomDeviceKind } from './roomDevice'

export type CheckInMethod = 'QR' | 'NFC'
export type CheckInOutcome = 'READY' | 'ALREADY_ACTIVE' | 'TOO_EARLY' | 'EXPIRED' | 'NO_MATCH'

export interface CheckInBooking {
  id: string
  startTime: string
  endTime: string
  reservedFor: string
}

export interface CheckInPreview {
  roomId: string
  roomName: string
  outcome: CheckInOutcome
  reservation: CheckInBooking | null
  checkInOpensAt: string | null
  /** Why check-in is not possible now, e.g. "Der Raum ist noch belegt …"; null when it is. */
  detail?: string | null
}

export interface DeviceStates {
  lighting: boolean
  ventilation: boolean
  door: 'LOCKED' | 'UNLOCKED'
}

export interface CheckInResult {
  reservationId: string
  status: 'ACTIVE'
  alreadyActive: boolean
  checkInMethod: CheckInMethod | 'MANUAL'
  checkedInAt: string
  devices: DeviceStates | null
  failedDevices: Exclude<RoomDeviceKind, 'PROJECTOR'>[]
}

export type RoomDeviceKind = 'LIGHTING' | 'VENTILATION' | 'PROJECTOR' | 'DOOR'

export interface RoomDevice {
  kind: RoomDeviceKind
  enabled: boolean
  state: boolean
  updatedAt: string
}

export interface RoomDeviceControlsResponse {
  roomId: string
  reservationId: string
  devices: RoomDevice[]
}

export interface RoomDeviceCommandRequest { state: boolean }

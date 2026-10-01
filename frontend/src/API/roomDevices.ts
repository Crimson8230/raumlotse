import { apiRequest } from './client'
import type { RoomDevice, RoomDeviceCommandRequest, RoomDeviceControlsResponse, RoomDeviceKind } from '../types/roomDevice'

export function getRoomDeviceControls(roomId: string): Promise<RoomDeviceControlsResponse> {
  return apiRequest<RoomDeviceControlsResponse>(`/api/rooms/${roomId}/device-controls`)
}

export function setRoomDeviceState(roomId: string, kind: RoomDeviceKind, request: RoomDeviceCommandRequest): Promise<RoomDevice> {
  return apiRequest<RoomDevice>(`/api/rooms/${roomId}/device-controls/${kind}`, {
    method: 'POST', body: JSON.stringify(request),
  })
}

import { apiRequest } from './client'
import type { RoomStatus } from '../types/roomStatus'

export function getRoomStatus(roomId: string): Promise<RoomStatus> {
  return apiRequest<RoomStatus>(`/api/rooms/${roomId}/status`)
}

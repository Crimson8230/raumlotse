import { apiRequest } from './client'
import type { Room, RoomCreateRequest, RoomUpdateRequest, StatusFilter } from '../types/room'

const BASE = '/api/rooms'

export function listRooms(status: StatusFilter = 'active'): Promise<Room[]> {
  return apiRequest<Room[]>(`${BASE}?status=${status}`)
}

export function getRoom(id: string): Promise<Room> {
  return apiRequest<Room>(`${BASE}/${id}`)
}

export function createRoom(request: RoomCreateRequest): Promise<Room> {
  return apiRequest<Room>(BASE, { method: 'POST', body: JSON.stringify(request) })
}

export function updateRoom(id: string, request: RoomUpdateRequest): Promise<Room> {
  return apiRequest<Room>(`${BASE}/${id}`, { method: 'PUT', body: JSON.stringify(request) })
}

export function deactivateRoom(id: string): Promise<Room> {
  return apiRequest<Room>(`${BASE}/${id}/deactivate`, { method: 'POST' })
}

export function reactivateRoom(id: string): Promise<Room> {
  return apiRequest<Room>(`${BASE}/${id}/reactivate`, { method: 'POST' })
}

export function deleteRoom(id: string): Promise<void> {
  return apiRequest<void>(`${BASE}/${id}`, { method: 'DELETE' })
}

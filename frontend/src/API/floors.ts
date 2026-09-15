import { apiRequest } from './client'
import type { Floor, StatusFilter } from '../types/room'

export function listFloors(buildingId: string, status: StatusFilter = 'all'): Promise<Floor[]> {
  return apiRequest<Floor[]>(`/api/buildings/${buildingId}/floors?status=${status}`)
}

export function createFloor(buildingId: string, name: string): Promise<Floor> {
  return apiRequest<Floor>(`/api/buildings/${buildingId}/floors`, {
    method: 'POST',
    body: JSON.stringify({ name }),
  })
}

export function renameFloor(id: string, name: string): Promise<Floor> {
  return apiRequest<Floor>(`/api/floors/${id}`, { method: 'PUT', body: JSON.stringify({ name }) })
}

export function deactivateFloor(id: string): Promise<Floor> {
  return apiRequest<Floor>(`/api/floors/${id}/deactivate`, { method: 'POST' })
}

export function reactivateFloor(id: string): Promise<Floor> {
  return apiRequest<Floor>(`/api/floors/${id}/reactivate`, { method: 'POST' })
}

export function deleteFloor(id: string): Promise<void> {
  return apiRequest<void>(`/api/floors/${id}`, { method: 'DELETE' })
}

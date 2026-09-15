import { apiRequest } from './client'
import type { Building, StatusFilter } from '../types/room'

const BASE = '/api/buildings'

export function listBuildings(status: StatusFilter = 'all'): Promise<Building[]> {
  return apiRequest<Building[]>(`${BASE}?status=${status}`)
}

export function createBuilding(name: string): Promise<Building> {
  return apiRequest<Building>(BASE, { method: 'POST', body: JSON.stringify({ name }) })
}

export function renameBuilding(id: string, name: string): Promise<Building> {
  return apiRequest<Building>(`${BASE}/${id}`, { method: 'PUT', body: JSON.stringify({ name }) })
}

export function deactivateBuilding(id: string): Promise<Building> {
  return apiRequest<Building>(`${BASE}/${id}/deactivate`, { method: 'POST' })
}

export function reactivateBuilding(id: string): Promise<Building> {
  return apiRequest<Building>(`${BASE}/${id}/reactivate`, { method: 'POST' })
}

export function deleteBuilding(id: string): Promise<void> {
  return apiRequest<void>(`${BASE}/${id}`, { method: 'DELETE' })
}

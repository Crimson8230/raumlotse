import { apiRequest } from './client'
import type { EquipmentType, StatusFilter } from '../types/room'

const BASE = '/api/equipment-types'

export function listEquipmentTypes(status: StatusFilter = 'all'): Promise<EquipmentType[]> {
  return apiRequest<EquipmentType[]>(`${BASE}?status=${status}`)
}

export function createEquipmentType(name: string): Promise<EquipmentType> {
  return apiRequest<EquipmentType>(BASE, { method: 'POST', body: JSON.stringify({ name }) })
}

export function renameEquipmentType(id: string, name: string): Promise<EquipmentType> {
  return apiRequest<EquipmentType>(`${BASE}/${id}`, { method: 'PUT', body: JSON.stringify({ name }) })
}

export function deactivateEquipmentType(id: string): Promise<EquipmentType> {
  return apiRequest<EquipmentType>(`${BASE}/${id}/deactivate`, { method: 'POST' })
}

export function reactivateEquipmentType(id: string): Promise<EquipmentType> {
  return apiRequest<EquipmentType>(`${BASE}/${id}/reactivate`, { method: 'POST' })
}

export function deleteEquipmentType(id: string): Promise<void> {
  return apiRequest<void>(`${BASE}/${id}`, { method: 'DELETE' })
}

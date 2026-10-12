import { apiRequest } from './client'
import type { EquipmentType, StatusFilter } from '../types/room'

const BASE = '/api/equipment-types'

export function listEquipmentTypes(status: StatusFilter = 'all'): Promise<EquipmentType[]> {
  return apiRequest<EquipmentType[]>(`${BASE}?status=${status}`)
}

/** Omitted `specialised` creates a general type (feature 017). */
export function createEquipmentType(name: string, code?: string, specialised?: boolean): Promise<EquipmentType> {
  return apiRequest<EquipmentType>(BASE, { method: 'POST', body: JSON.stringify({ name, code, specialised }) })
}

/** Omitted `specialised` keeps the current classification (feature 017). */
export function renameEquipmentType(
  id: string,
  name: string,
  code?: string,
  specialised?: boolean,
): Promise<EquipmentType> {
  return apiRequest<EquipmentType>(`${BASE}/${id}`, { method: 'PUT', body: JSON.stringify({ name, code, specialised }) })
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

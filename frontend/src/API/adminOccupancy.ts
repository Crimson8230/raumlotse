import { apiRequest } from './client'
import type { OccupancyForecast, OccupancySnapshot } from '../types/adminOccupancy'

const BASE = '/api/admin/occupancy'

export function getOccupancySnapshot(buildingId?: string): Promise<OccupancySnapshot> {
  const query = buildingId ? `?${new URLSearchParams({ buildingId }).toString()}` : ''
  return apiRequest<OccupancySnapshot>(`${BASE}${query}`)
}

export function getOccupancyForecast(date: string, buildingId?: string): Promise<OccupancyForecast> {
  const query = new URLSearchParams({ date })
  if (buildingId) query.set('buildingId', buildingId)
  return apiRequest<OccupancyForecast>(`${BASE}/forecast?${query.toString()}`)
}

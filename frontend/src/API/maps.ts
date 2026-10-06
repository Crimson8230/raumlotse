import { apiRequest } from './client'
import type { Connection, ConnectionType, MapDetail, MapSummary, Placement, Position, RoomRef } from '../types/map'

export function listMaps(): Promise<MapSummary[]> {
  return apiRequest<MapSummary[]>('/api/maps')
}

export function getMap(mapId: string): Promise<MapDetail> {
  return apiRequest<MapDetail>(`/api/maps/${mapId}`)
}

/** Creates the map of a floor, or replaces its image when the floor already has one. */
export function uploadMapImage(floorId: string, image: File): Promise<MapSummary> {
  const body = new FormData()
  body.append('image', image)
  return apiRequest<MapSummary>(`/api/floors/${floorId}/map`, { method: 'PUT', body })
}

export function deleteMap(mapId: string): Promise<void> {
  return apiRequest<void>(`/api/maps/${mapId}`, { method: 'DELETE' })
}

/** The version query parameter busts the browser cache when the image is replaced. */
export function mapImageUrl(map: Pick<MapSummary, 'id' | 'imageVersion'>): string {
  return `/api/maps/${map.id}/image?v=${map.imageVersion}`
}

export function placeRoom(mapId: string, roomId: string, position: Position): Promise<Placement> {
  return apiRequest<Placement>(`/api/maps/${mapId}/placements/${roomId}`, {
    method: 'PUT',
    body: JSON.stringify(position),
  })
}

export function removePlacement(mapId: string, roomId: string): Promise<void> {
  return apiRequest<void>(`/api/maps/${mapId}/placements/${roomId}`, { method: 'DELETE' })
}

export function listUnplacedRooms(mapId: string): Promise<RoomRef[]> {
  return apiRequest<RoomRef[]>(`/api/maps/${mapId}/unplaced-rooms`)
}

export interface ConnectionInput {
  name: string
  type: ConnectionType
}

export function listConnections(): Promise<Connection[]> {
  return apiRequest<Connection[]>('/api/connections')
}

export function createConnection(input: ConnectionInput): Promise<Connection> {
  return apiRequest<Connection>('/api/connections', { method: 'POST', body: JSON.stringify(input) })
}

export function updateConnection(id: string, input: ConnectionInput): Promise<Connection> {
  return apiRequest<Connection>(`/api/connections/${id}`, { method: 'PUT', body: JSON.stringify(input) })
}

export function deleteConnection(id: string): Promise<void> {
  return apiRequest<void>(`/api/connections/${id}`, { method: 'DELETE' })
}

/** Adds the connection's point on the map or moves it when it already has one there. */
export function putConnectionPoint(connectionId: string, mapId: string, position: Position): Promise<Connection> {
  return apiRequest<Connection>(`/api/connections/${connectionId}/points/${mapId}`, {
    method: 'PUT',
    body: JSON.stringify(position),
  })
}

export function deleteConnectionPoint(connectionId: string, mapId: string): Promise<void> {
  return apiRequest<void>(`/api/connections/${connectionId}/points/${mapId}`, { method: 'DELETE' })
}

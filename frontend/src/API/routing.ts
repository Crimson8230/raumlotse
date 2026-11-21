import { apiRequest } from './client'
import type { Neighbors, Route } from '../types/routing'

/** Nearest neighbors of a room on the same floor plus stairs/elevators up and down (read-only). */
export function getRoomNeighbors(roomId: string): Promise<Neighbors> {
  return apiRequest<Neighbors>(`/api/rooms/${roomId}/neighbors`)
}

/** Shortest route and estimated walking time between two rooms; rejects with a Problem (409) when impossible. */
export function getRoute(fromRoomId: string, toRoomId: string, accessibleOnly = false): Promise<Route> {
  const query = new URLSearchParams({ fromRoomId, toRoomId, accessibleOnly: String(accessibleOnly) })
  return apiRequest<Route>(`/api/routes?${query.toString()}`)
}

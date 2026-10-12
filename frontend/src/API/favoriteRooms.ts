import { apiRequest } from './client'
import type { FavoriteRoom } from '../types/room'

const BASE = '/api/me/favorite-rooms'

/** Favorites of the signed-in user, including rooms that are no longer available (feature 017). */
export function listFavoriteRooms(): Promise<FavoriteRoom[]> {
  return apiRequest<FavoriteRoom[]>(BASE)
}

export function markFavoriteRoom(roomId: string): Promise<void> {
  return apiRequest<void>(`${BASE}/${roomId}`, { method: 'PUT' })
}

export function unmarkFavoriteRoom(roomId: string): Promise<void> {
  return apiRequest<void>(`${BASE}/${roomId}`, { method: 'DELETE' })
}

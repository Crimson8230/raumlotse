import { describe, expect, it, vi } from 'vitest'
import { apiRequest } from './client'
import { listFavoriteRooms, markFavoriteRoom, unmarkFavoriteRoom } from './favoriteRooms'

vi.mock('./client', () => ({ apiRequest: vi.fn() }))
const request = vi.mocked(apiRequest)

describe('favorite rooms API', () => {
  it('lists the favorites of the signed-in user', async () => {
    request.mockResolvedValue([])
    await listFavoriteRooms()
    expect(request).toHaveBeenCalledWith('/api/me/favorite-rooms')
  })

  it('marks a room as favorite', async () => {
    request.mockResolvedValue(undefined)
    await markFavoriteRoom('r1')
    expect(request).toHaveBeenCalledWith('/api/me/favorite-rooms/r1', { method: 'PUT' })
  })

  it('removes the favorite mark', async () => {
    request.mockResolvedValue(undefined)
    await unmarkFavoriteRoom('r1')
    expect(request).toHaveBeenCalledWith('/api/me/favorite-rooms/r1', { method: 'DELETE' })
  })
})

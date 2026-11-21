import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiRequest } from './client'
import { getRoomNeighbors, getRoute } from './routing'

vi.mock('./client', () => ({ apiRequest: vi.fn() }))
const request = vi.mocked(apiRequest)

beforeEach(() => vi.resetAllMocks())

describe('routing API', () => {
  it('reads the neighbors of a room through the shared client', async () => {
    request.mockResolvedValue({ roomId: 'r1', available: true, reason: null, entries: [] })
    await getRoomNeighbors('r1')
    expect(request).toHaveBeenCalledWith('/api/rooms/r1/neighbors')
  })

  it('reads a route with the accessible-only option', async () => {
    request.mockResolvedValue({})
    await getRoute('a', 'b')
    expect(request).toHaveBeenCalledWith('/api/routes?fromRoomId=a&toRoomId=b&accessibleOnly=false')
    await getRoute('a', 'b', true)
    expect(request).toHaveBeenLastCalledWith('/api/routes?fromRoomId=a&toRoomId=b&accessibleOnly=true')
  })
})

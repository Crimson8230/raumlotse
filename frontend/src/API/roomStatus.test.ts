import { describe, expect, it, vi } from 'vitest'
import { apiRequest } from './client'
import { getRoomStatus } from './roomStatus'

vi.mock('./client', () => ({ apiRequest: vi.fn() }))
const request = vi.mocked(apiRequest)

describe('room status API', () => {
  it('reads the room status', async () => {
    request.mockResolvedValue({ roomId: 'r1', status: 'AVAILABLE', devices: { lighting: false, ventilation: false, door: 'LOCKED' }, lastPresenceAt: null })
    await getRoomStatus('r1')
    expect(request).toHaveBeenCalledWith('/api/rooms/r1/status')
  })
})

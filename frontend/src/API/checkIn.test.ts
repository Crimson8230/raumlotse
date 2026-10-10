import { describe, expect, it, vi } from 'vitest'
import { apiRequest } from './client'
import { checkIn, getCheckInPreview } from './checkIn'

vi.mock('./client', () => ({ apiRequest: vi.fn() }))
const request = vi.mocked(apiRequest)

describe('check-in API', () => {
  it('loads the preview for a room', async () => {
    request.mockResolvedValue({ roomId: 'r1', roomName: 'A', outcome: 'NO_MATCH', reservation: null, checkInOpensAt: null })
    await getCheckInPreview('r1')
    expect(request).toHaveBeenCalledWith('/api/rooms/r1/check-in')
  })

  it('posts the method', async () => {
    request.mockResolvedValue({})
    await checkIn('r1', 'NFC')
    expect(request).toHaveBeenCalledWith('/api/rooms/r1/check-in', expect.objectContaining({ method: 'POST', body: '{"method":"NFC"}' }))
  })
})

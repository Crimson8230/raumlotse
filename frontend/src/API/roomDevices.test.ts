import { describe, expect, it, vi } from 'vitest'
import { apiRequest } from './client'
import { getRoomDeviceControls, setRoomDeviceState } from './roomDevices'

vi.mock('./client', () => ({ apiRequest: vi.fn() }))
const request = vi.mocked(apiRequest)

describe('room device API', () => {
  it('loads capabilities through the shared client', async () => {
    request.mockResolvedValue({ roomId: 'r1', reservationId: 'b1', devices: [] })
    await getRoomDeviceControls('r1')
    expect(request).toHaveBeenCalledWith('/api/rooms/r1/device-controls')
  })

  it('posts a typed boolean command', async () => {
    request.mockResolvedValue({ kind: 'LIGHTING', enabled: true, state: true, updatedAt: '2026-10-01T10:00:00Z' })
    await setRoomDeviceState('r1', 'LIGHTING', { state: true })
    expect(request).toHaveBeenCalledWith('/api/rooms/r1/device-controls/LIGHTING', expect.objectContaining({ method: 'POST', body: '{"state":true}' }))
  })
})

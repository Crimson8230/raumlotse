import { describe, expect, it, vi } from 'vitest'
import { apiRequest } from './client'
import { getCheckInSettings, saveCheckInSettings } from './checkInSettings'

vi.mock('./client', () => ({ apiRequest: vi.fn() }))
const request = vi.mocked(apiRequest)

describe('check-in settings API', () => {
  it('reads the settings', async () => {
    request.mockResolvedValue({ earlyCheckInMinutes: 10, gracePeriodMinutes: 5, updatedAt: '2026-10-10T08:00:00Z' })
    await getCheckInSettings()
    expect(request).toHaveBeenCalledWith('/api/admin/check-in-settings')
  })

  it('saves both values', async () => {
    request.mockResolvedValue({ earlyCheckInMinutes: 0, gracePeriodMinutes: 15, updatedAt: '2026-10-10T08:00:00Z' })
    await saveCheckInSettings({ earlyCheckInMinutes: 0, gracePeriodMinutes: 15 })
    expect(request).toHaveBeenCalledWith('/api/admin/check-in-settings', expect.objectContaining({
      method: 'PUT', body: '{"earlyCheckInMinutes":0,"gracePeriodMinutes":15}',
    }))
  })
})

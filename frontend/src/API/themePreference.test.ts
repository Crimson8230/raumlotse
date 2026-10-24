import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiRequest } from './client'
import { getThemePreference, saveThemePreference, skipThemeOnboarding } from './themePreference'

vi.mock('./client', () => ({ apiRequest: vi.fn() }))
const request = vi.mocked(apiRequest)

beforeEach(() => vi.resetAllMocks())

describe('personal theme preference API', () => {
  it('reads and validates the preference response', async () => {
    request.mockResolvedValue({ effectiveTheme: 'DARK', selectedTheme: 'DARK', onboardingPending: false })
    await expect(getThemePreference()).resolves.toEqual({ effectiveTheme: 'DARK', selectedTheme: 'DARK', onboardingPending: false })
    expect(request).toHaveBeenCalledWith('/api/me/theme-preference')
  })

  it('rejects unsupported response codes', async () => {
    request.mockResolvedValue({ effectiveTheme: 'BLUE', selectedTheme: null, onboardingPending: false })
    await expect(getThemePreference()).rejects.toThrow()
  })

  it('saves a theme through the typed request', async () => {
    request.mockResolvedValue({ effectiveTheme: 'LIGHT', selectedTheme: 'LIGHT', onboardingPending: false })
    await saveThemePreference('LIGHT')
    expect(request).toHaveBeenCalledWith('/api/me/theme-preference', {
      method: 'PUT', body: JSON.stringify({ theme: 'LIGHT' }),
    })
  })

  it('skips onboarding without clearing a saved choice', async () => {
    request.mockResolvedValue({ effectiveTheme: 'DARK', selectedTheme: 'DARK', onboardingPending: false })
    await expect(skipThemeOnboarding()).resolves.toMatchObject({ selectedTheme: 'DARK' })
    expect(request).toHaveBeenCalledWith('/api/me/theme-preference/skip', { method: 'POST' })
  })

  it('passes request failures to the caller', async () => {
    request.mockRejectedValue(new Error('offline'))
    await expect(getThemePreference()).rejects.toThrow('offline')
  })
})

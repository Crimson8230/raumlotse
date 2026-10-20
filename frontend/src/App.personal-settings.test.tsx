import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'

const auth = vi.hoisted(() => ({ me: vi.fn(), login: vi.fn(), refreshCsrfToken: vi.fn() }))
const theme = vi.hoisted(() => ({ getThemePreference: vi.fn(), saveThemePreference: vi.fn(), skipThemeOnboarding: vi.fn() }))
vi.mock('./API/auth', () => ({ authApi: auth }))
vi.mock('./API/themePreference', () => ({ ...theme }))
vi.mock('./API/client', () => ({
  ApiError: class ApiError extends Error { readonly status: number; constructor(status: number) { super(); this.status = status } },
  clearCsrfToken: vi.fn(), apiRequest: vi.fn().mockResolvedValue([]),
}))

describe('personal settings route', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    auth.me.mockResolvedValue({ userId: 'u1', displayName: 'User' })
    auth.refreshCsrfToken.mockResolvedValue(undefined)
    theme.getThemePreference.mockResolvedValue({ effectiveTheme: 'DARK', selectedTheme: 'DARK', onboardingPending: false })
  })

  it('opens /settings without administration mode and applies the persisted theme', async () => {
    render(<MemoryRouter initialEntries={['/settings']}><App /></MemoryRouter>)
    expect(await screen.findByRole('heading', { name: 'Persönliche Einstellungen' })).toBeInTheDocument()
    expect(document.documentElement).toHaveAttribute('data-theme', 'dark')
  })
})

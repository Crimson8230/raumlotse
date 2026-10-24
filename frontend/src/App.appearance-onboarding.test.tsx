import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'

const auth = vi.hoisted(() => ({ me: vi.fn(), login: vi.fn(), refreshCsrfToken: vi.fn() }))
const theme = vi.hoisted(() => ({ getThemePreference: vi.fn(), saveThemePreference: vi.fn(), skipThemeOnboarding: vi.fn() }))
vi.mock('./API/auth', () => ({ authApi: auth }))
vi.mock('./API/themePreference', () => ({ ...theme }))
vi.mock('./API/health', () => ({ getHealth: vi.fn().mockResolvedValue({ status: 'ok' }) }))
vi.mock('./API/client', () => ({
  ApiError: class ApiError extends Error { readonly status: number; constructor(status: number) { super(); this.status = status } },
  clearCsrfToken: vi.fn(), apiRequest: vi.fn().mockResolvedValue([]),
}))

describe('appearance onboarding routes', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    auth.me.mockResolvedValue({ userId: 'u1', displayName: 'User' })
    auth.refreshCsrfToken.mockResolvedValue(undefined)
    theme.getThemePreference.mockResolvedValue({ effectiveTheme: 'LIGHT', selectedTheme: null, onboardingPending: true })
    theme.saveThemePreference.mockResolvedValue({ effectiveTheme: 'DARK', selectedTheme: 'DARK', onboardingPending: false })
    theme.skipThemeOnboarding.mockResolvedValue({ effectiveTheme: 'LIGHT', selectedTheme: null, onboardingPending: false })
  })

  it('shows first choice before home and applies the saved dark theme', async () => {
    const user = userEvent.setup()
    render(<MemoryRouter initialEntries={['/']}><App /></MemoryRouter>)
    expect(await screen.findByRole('heading', { name: /Farbschema auswählen/ })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Raumlotse' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('radio', { name: /Dunkel/ }))
    await user.click(screen.getByRole('button', { name: 'Auswahl speichern' }))
    expect(await screen.findByRole('heading', { name: 'Raumlotse' })).toBeInTheDocument()
    expect(document.documentElement).toHaveAttribute('data-theme', 'dark')
  })

  it('does not prompt a pre-existing account', async () => {
    theme.getThemePreference.mockResolvedValue({ effectiveTheme: 'LIGHT', selectedTheme: null, onboardingPending: false })
    render(<MemoryRouter initialEntries={['/']}><App /></MemoryRouter>)
    expect(await screen.findByRole('heading', { name: 'Raumlotse' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: /Farbschema auswählen/ })).not.toBeInTheDocument()
  })

  it('can skip onboarding and continue to the home page', async () => {
    const user = userEvent.setup()
    render(<MemoryRouter initialEntries={['/']}><App /></MemoryRouter>)
    expect(await screen.findByRole('heading', { name: /Farbschema auswählen/ })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Später auswählen' }))
    expect(await screen.findByRole('heading', { name: 'Raumlotse' })).toBeInTheDocument()
    expect(theme.skipThemeOnboarding).toHaveBeenCalledOnce()
  })

  it('retries a failed preference fetch before showing protected content', async () => {
    theme.getThemePreference.mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValue({ effectiveTheme: 'LIGHT', selectedTheme: null, onboardingPending: false })
    const user = userEvent.setup()
    render(<MemoryRouter initialEntries={['/']}><App /></MemoryRouter>)
    expect(await screen.findByRole('alert')).toHaveTextContent('Farbschema konnte nicht geladen werden')
    expect(screen.queryByRole('heading', { name: 'Raumlotse' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Erneut versuchen' }))
    expect(await screen.findByRole('heading', { name: 'Raumlotse' })).toBeInTheDocument()
    expect(theme.getThemePreference).toHaveBeenCalledTimes(2)
  })

  it('keeps a direct settings URL with query parameters after onboarding', async () => {
    const user = userEvent.setup()
    render(<MemoryRouter initialEntries={['/settings?origin=email']}><App /></MemoryRouter>)
    expect(await screen.findByRole('heading', { name: /Farbschema auswählen/ })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Später auswählen' }))
    expect(await screen.findByRole('heading', { name: 'Persönliche Einstellungen' })).toBeInTheDocument()
  })
})

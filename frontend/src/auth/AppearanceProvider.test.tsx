import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AppearanceProvider } from './AppearanceProvider'
import { RequireAppearance } from './RequireAppearance'

const auth = vi.hoisted(() => ({ state: 'authenticated' as string, identity: { userId: 'account-1' } }))
const theme = vi.hoisted(() => ({ getThemePreference: vi.fn(), saveThemePreference: vi.fn(), skipThemeOnboarding: vi.fn() }))
vi.mock('./useAuth', () => ({ useAuth: () => ({
  state: auth.state, user: auth.state === 'authenticated' ? auth.identity : null,
}) }))
vi.mock('../API/themePreference', () => ({ ...theme }))

function app() {
  return <MemoryRouter initialEntries={['/settings']}><AppearanceProvider><Routes>
    <Route path="/settings" element={<RequireAppearance><main>Einstellungen geladen</main></RequireAppearance>} />
  </Routes></AppearanceProvider></MemoryRouter>
}

describe('appearance after a new session', () => {
  beforeEach(() => {
    auth.state = 'authenticated'
    auth.identity = { userId: 'account-1' }
    vi.resetAllMocks()
    delete document.documentElement.dataset.theme
  })

  it('loads the same account again before showing its page or previous theme', async () => {
    let resolveNext!: (value: { effectiveTheme: 'LIGHT'; selectedTheme: 'LIGHT'; onboardingPending: false }) => void
    theme.getThemePreference.mockResolvedValueOnce({ effectiveTheme: 'DARK', selectedTheme: 'DARK', onboardingPending: false })
      .mockImplementationOnce(() => new Promise(resolve => { resolveNext = resolve }))
    const view = render(app())
    expect(await screen.findByText('Einstellungen geladen')).toBeInTheDocument()
    expect(document.documentElement).toHaveAttribute('data-theme', 'dark')

    auth.state = 'anonymous'
    view.rerender(app())
    await waitFor(() => expect(document.documentElement).not.toHaveAttribute('data-theme'))

    auth.state = 'authenticated'
    auth.identity = { userId: 'account-1' }
    view.rerender(app())
    expect(screen.getByText(/Farbschema wird geladen/)).toBeInTheDocument()
    expect(screen.queryByText('Einstellungen geladen')).not.toBeInTheDocument()
    expect(document.documentElement).not.toHaveAttribute('data-theme')
    expect(theme.getThemePreference).toHaveBeenCalledTimes(2)

    resolveNext({ effectiveTheme: 'LIGHT', selectedTheme: 'LIGHT', onboardingPending: false })
    expect(await screen.findByText('Einstellungen geladen')).toBeInTheDocument()
    expect(document.documentElement).not.toHaveAttribute('data-theme')
  })
})

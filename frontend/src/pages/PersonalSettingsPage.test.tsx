import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import PersonalSettingsPage from './PersonalSettingsPage'

const actions = vi.hoisted(() => ({ calls: [] as string[], fail: false }))
vi.mock('../auth/useAppearance', () => ({ useAppearance: () => ({
  preference: { effectiveTheme: 'LIGHT', selectedTheme: null, onboardingPending: false },
  choose: async (theme: string) => {
    if (actions.fail) throw new Error('Speichern fehlgeschlagen')
    actions.calls.push(theme)
  },
}) }))

beforeEach(() => { actions.calls = []; actions.fail = false })

describe('personal settings', () => {
  it('shows the effective theme and saves a deliberate choice', async () => {
    const user = userEvent.setup()
    render(<PersonalSettingsPage />)
    expect(screen.getByText(/Aktuell: Hell/)).toBeInTheDocument()
    await user.click(screen.getByRole('radio', { name: /Dunkel/ }))
    await user.click(screen.getByRole('button', { name: 'Farbschema speichern' }))
    expect(actions.calls).toEqual(['DARK'])
  })

  it('keeps the confirmed choice visible after a failed save', async () => {
    actions.fail = true
    const user = userEvent.setup()
    render(<PersonalSettingsPage />)
    await user.click(screen.getByRole('radio', { name: /Dunkel/ }))
    await user.click(screen.getByRole('button', { name: 'Farbschema speichern' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Speichern fehlgeschlagen')
    expect(screen.getByText(/Aktuell: Hell/)).toBeInTheDocument()
  })
})

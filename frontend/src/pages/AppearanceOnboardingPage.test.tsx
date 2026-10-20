import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import AppearanceOnboardingPage from './AppearanceOnboardingPage'

describe('first login theme choice', () => {
  it('offers named previews, saves the chosen theme and permits skipping', async () => {
    const choose = vi.fn().mockResolvedValue(undefined)
    const skip = vi.fn().mockResolvedValue(undefined)
    const user = userEvent.setup()
    render(<AppearanceOnboardingPage onChoose={choose} onSkip={skip} />)
    expect(screen.getByRole('radio', { name: /Hell/ })).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: /Dunkel/ })).toBeInTheDocument()
    await user.click(screen.getByRole('radio', { name: /Dunkel/ }))
    await user.click(screen.getByRole('button', { name: 'Auswahl speichern' }))
    expect(choose).toHaveBeenCalledWith('DARK')
    await user.click(screen.getByRole('button', { name: 'Später auswählen' }))
    expect(skip).toHaveBeenCalledOnce()
  })

  it('keeps the choice open and announces a save failure', async () => {
    const choose = vi.fn().mockRejectedValue(new Error('Speichern nicht möglich'))
    const user = userEvent.setup()
    render(<AppearanceOnboardingPage onChoose={choose} onSkip={vi.fn()} />)
    await user.click(screen.getByRole('button', { name: 'Auswahl speichern' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Speichern nicht möglich')
    expect(screen.getByRole('button', { name: 'Auswahl speichern' })).toBeEnabled()
  })

  it('announces a skip failure and lets the user retry', async () => {
    let attempts = 0
    const skip = async () => { attempts++; if (attempts === 1) throw new Error('Später nicht möglich') }
    const user = userEvent.setup()
    render(<AppearanceOnboardingPage onChoose={async () => undefined} onSkip={skip} />)
    await user.click(screen.getByRole('button', { name: 'Später auswählen' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Später nicht möglich')
    await user.click(screen.getByRole('button', { name: 'Später auswählen' }))
    expect(attempts).toBe(2)
  })

  it('supports choosing a theme using the keyboard', async () => {
    const selected: string[] = []
    const user = userEvent.setup()
    render(<AppearanceOnboardingPage onChoose={async theme => { selected.push(theme) }} onSkip={async () => undefined} />)
    await user.tab()
    expect(screen.getByRole('radio', { name: 'Hell' })).toHaveFocus()
    await user.keyboard('{ArrowDown}')
    expect(screen.getByRole('radio', { name: 'Dunkel' })).toBeChecked()
    await user.tab()
    expect(screen.getByRole('button', { name: 'Auswahl speichern' })).toHaveFocus()
    await user.keyboard('{Enter}')
    expect(selected).toEqual(['DARK'])
  })

  it('describes both visual previews to assistive technology', () => {
    render(<AppearanceOnboardingPage onChoose={async () => undefined} onSkip={async () => undefined} />)
    expect(screen.getByRole('radio', { name: 'Hell' })).toHaveAccessibleDescription('Heller Hintergrund mit dunklem Text und Schaltfläche.')
    expect(screen.getByRole('radio', { name: 'Dunkel' })).toHaveAccessibleDescription('Dunkler Hintergrund mit hellem Text und Schaltfläche.')
  })
})

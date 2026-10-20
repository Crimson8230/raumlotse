import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { RequireAuth } from './RequireAuth'
import type { ReactNode } from 'react'

vi.mock('./RequireAppearance', () => ({ RequireAppearance: ({ children }: { children: ReactNode }) => children }))

const auth = vi.hoisted(() => ({ state: 'authenticated' as string }))
vi.mock('./useAuth', () => ({ useAuth: () => ({ state: auth.state }) }))
vi.mock('../components/Navigation/Navigation', () => ({
  Navigation: () => <nav aria-label="Hauptnavigation"><a href="/rooms">Räume</a></nav>,
}))

function renderFrame() {
  return render(
    <MemoryRouter initialEntries={['/seite']}>
      <Routes>
        <Route element={<RequireAuth />}>
          <Route path="/seite" element={<main id="inhalt"><button type="button">Im Inhalt</button></main>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  )
}

describe('RequireAuth frame (feature 019, US1)', () => {
  it('offers the skip link as first focusable element, before the navigation', async () => {
    auth.state = 'authenticated'
    renderFrame()
    await userEvent.tab()
    expect(screen.getByRole('link', { name: 'Zum Inhalt springen' })).toHaveFocus()
  })

  it('moves focus into the main content when the skip link is activated', async () => {
    auth.state = 'authenticated'
    renderFrame()
    await userEvent.tab()
    await userEvent.keyboard('{Enter}')
    expect(screen.getByRole('main')).toHaveFocus()
    await userEvent.tab()
    expect(screen.getByRole('button', { name: 'Im Inhalt' })).toHaveFocus()
  })

  it('keeps exactly one main content area while the sign-in is checked', () => {
    auth.state = 'loading'
    renderFrame()
    expect(screen.getAllByRole('main')).toHaveLength(1)
    expect(screen.getByRole('main')).toHaveAttribute('id', 'inhalt')
  })

  it('keeps exactly one main content area when the sign-in is unavailable', () => {
    auth.state = 'unavailable'
    renderFrame()
    expect(screen.getAllByRole('main')).toHaveLength(1)
    expect(screen.getByRole('main')).toHaveAttribute('id', 'inhalt')
  })
})

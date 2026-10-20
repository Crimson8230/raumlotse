import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { RequireAuth } from './RequireAuth'
import type { ReactNode } from 'react'

vi.mock('./RequireAppearance', () => ({ RequireAppearance: ({ children }: { children: ReactNode }) => children }))

const auth = vi.hoisted(() => ({ state: 'anonymous' }))
vi.mock('./useAuth', () => ({ useAuth: () => ({ state: auth.state }) }))
vi.mock('../components/Navigation/Navigation', () => ({ Navigation: () => null }))

function LoginProbe() {
  const location = useLocation()
  return <p>from: {(location.state as { from?: string } | null)?.from}</p>
}

describe('RequireAuth', () => {
  beforeEach(() => { auth.state = 'anonymous' })

  it('remembers the full path including the query string for after login', () => {
    render(
      <MemoryRouter initialEntries={['/rooms/r1/check-in?method=nfc']}>
        <Routes>
          <Route path="/login" element={<LoginProbe />} />
          <Route element={<RequireAuth />}>
            <Route path="/rooms/:roomId/check-in" element={<p>check-in</p>} />
          </Route>
        </Routes>
      </MemoryRouter>,
    )
    expect(screen.getByText('from: /rooms/r1/check-in?method=nfc')).toBeInTheDocument()
  })

  it('shows the help footer after the main content when signed in (feature 020)', () => {
    auth.state = 'authenticated'
    render(
      <MemoryRouter initialEntries={['/x']}>
        <Routes>
          <Route element={<RequireAuth />}>
            <Route path="/x" element={<main id="inhalt">x</main>} />
          </Route>
        </Routes>
      </MemoryRouter>,
    )
    const main = screen.getByRole('main')
    const footer = screen.getByRole('contentinfo')
    expect(main.compareDocumentPosition(footer) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    expect(screen.getByRole('link', { name: 'Hilfe' })).toHaveAttribute('href', '/hilfe')
  })
})

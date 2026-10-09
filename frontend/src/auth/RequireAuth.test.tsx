import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { RequireAuth } from './RequireAuth'

vi.mock('./useAuth', () => ({ useAuth: () => ({ state: 'anonymous' }) }))
vi.mock('../components/Navigation/Navigation', () => ({ Navigation: () => null }))

function LoginProbe() {
  const location = useLocation()
  return <p>from: {(location.state as { from?: string } | null)?.from}</p>
}

describe('RequireAuth', () => {
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
})

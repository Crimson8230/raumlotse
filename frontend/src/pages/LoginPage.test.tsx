import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { AuthProvider } from '../auth/AuthProvider'
import { ApiError } from '../API/client'
import LoginPage from './LoginPage'

const auth = vi.hoisted(() => ({
  me: vi.fn(),
  login: vi.fn(),
  refreshCsrfToken: vi.fn(),
}))
vi.mock('../API/auth', () => ({ authApi: auth }))
vi.mock('../API/client', () => ({
  ApiError: class ApiError extends Error {
    readonly status: number
    constructor(status: number) { super('request failed'); this.status = status }
  },
}))

function renderLogin() {
  return render(<MemoryRouter><AuthProvider><LoginPage /></AuthProvider></MemoryRouter>)
}

function LocationProbe() {
  const location = useLocation()
  return <p>Ziel: {location.pathname + location.search}</p>
}

function renderLoginFrom(from: unknown) {
  return render(
    <MemoryRouter initialEntries={[{ pathname: '/login', state: { from } }]}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="*" element={<LocationProbe />} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  )
}

describe('LoginPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    auth.refreshCsrfToken.mockResolvedValue(undefined)
    auth.me.mockRejectedValue(new ApiError(401))
  })

  afterEach(() => vi.useRealTimers())

  it('masks the password and provides browser autofill hints', async () => {
    renderLogin()
    const email = await screen.findByLabelText('E-Mail-Adresse')
    const password = screen.getByLabelText('Passwort')
    expect(email).toHaveAttribute('autocomplete', 'username')
    expect(password).toHaveAttribute('type', 'password')
    expect(password).toHaveAttribute('autocomplete', 'current-password')
  })

  it('preserves the exact password and announces generic credential errors', async () => {
    auth.login.mockRejectedValue(new ApiError(401))
    const user = userEvent.setup()
    renderLogin()
    await user.type(await screen.findByLabelText('E-Mail-Adresse'), 'user@example.test')
    await user.type(screen.getByLabelText('Passwort'), '  Exact Case  ')
    await user.click(screen.getByRole('button', { name: 'Anmelden' }))
    expect(auth.login).toHaveBeenCalledWith({ email: 'user@example.test', password: '  Exact Case  ' })
    expect(await screen.findByRole('alert')).toHaveTextContent('E-Mail-Adresse oder Passwort ist falsch.')
  })

  it('shows the per-email cooldown, clears the password and leaves other email keys usable', async () => {
    auth.login.mockRejectedValue(Object.assign(new ApiError(401), { retryAfterSeconds: 900 }))
    const user = userEvent.setup()
    renderLogin()
    const email = await screen.findByLabelText('E-Mail-Adresse')
    const password = screen.getByLabelText('Passwort')
    await user.type(email, 'user@example.test')
    await user.type(password, 'wrong')
    await user.click(screen.getByRole('button', { name: 'Anmelden' }))
    expect(await screen.findByRole('status')).toHaveTextContent('15:00')
    expect(password).toHaveValue('')
    expect(screen.getByRole('button', { name: 'Anmelden' })).toBeDisabled()
    await user.clear(email)
    await user.type(email, 'another@example.test')
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Anmelden' })).toBeEnabled()
  })

  it('does not retry automatically when a cooldown expires', async () => {
    auth.login.mockRejectedValueOnce(Object.assign(new ApiError(429), { retryAfterSeconds: 1 }))
      .mockRejectedValueOnce(new ApiError(401))
    const user = userEvent.setup()
    renderLogin()
    await user.type(await screen.findByLabelText('E-Mail-Adresse'), 'user@example.test')
    await user.type(screen.getByLabelText('Passwort'), 'wrong')
    await user.click(screen.getByRole('button', { name: 'Anmelden' }))
    await waitFor(() => expect(screen.getByRole('button', { name: 'Anmelden' })).toBeEnabled(), { timeout: 2500 })
    expect(auth.login).toHaveBeenCalledTimes(1)
    await user.type(screen.getByLabelText('Passwort'), 'wrong-again')
    await user.click(screen.getByRole('button', { name: 'Anmelden' }))
    expect(auth.login).toHaveBeenCalledTimes(2)
  })

  it('recomputes the cooldown on focus and visibility without polling or automatic retry', async () => {
    vi.useFakeTimers()
    const start = new Date('2026-09-21T12:00:00Z')
    vi.setSystemTime(start)
    auth.login.mockRejectedValueOnce(Object.assign(new ApiError(429), { retryAfterSeconds: 30 }))
      .mockRejectedValueOnce(new ApiError(401))
    renderLogin()
    await act(async () => { await Promise.resolve(); await Promise.resolve() })
    const email = screen.getByLabelText('E-Mail-Adresse')
    const password = screen.getByLabelText('Passwort')
    fireEvent.change(email, { target: { value: 'user@example.test' } })
    fireEvent.change(password, { target: { value: 'wrong' } })
    await act(async () => {
      fireEvent.submit(screen.getByRole('form', { name: 'Anmelden' }))
      await Promise.resolve()
      await Promise.resolve()
    })
    expect(screen.getByRole('status')).toHaveTextContent('0:30')
    expect(auth.login).toHaveBeenCalledTimes(1)

    act(() => { vi.advanceTimersByTime(1000) })
    expect(screen.getByRole('status')).toHaveTextContent('0:29')
    vi.setSystemTime(new Date(start.getTime() + 31_000))
    act(() => {
      window.dispatchEvent(new Event('focus'))
      document.dispatchEvent(new Event('visibilitychange'))
    })
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Anmelden' })).toBeEnabled()
    expect(auth.login).toHaveBeenCalledTimes(1)

    fireEvent.change(password, { target: { value: 'corrected' } })
    await act(async () => {
      fireEvent.submit(screen.getByRole('form', { name: 'Anmelden' }))
      await Promise.resolve()
      await Promise.resolve()
    })
    expect(auth.login).toHaveBeenCalledTimes(2)
  })

  it('returns to the page the user came from after signing in', async () => {
    auth.login.mockResolvedValue({ userId: 'u1', displayName: 'Erika' })
    const user = userEvent.setup()
    renderLoginFrom('/rooms/r1/check-in?method=nfc')
    await user.type(await screen.findByLabelText('E-Mail-Adresse'), 'user@example.test')
    await user.type(screen.getByLabelText('Passwort'), 'secret')
    await user.click(screen.getByRole('button', { name: 'Anmelden' }))
    expect(await screen.findByText('Ziel: /rooms/r1/check-in?method=nfc')).toBeInTheDocument()
  })

  it.each(['//evil.example', 'https://evil.example', undefined])('goes to the start page for the unsafe target %s', async (from) => {
    auth.login.mockResolvedValue({ userId: 'u1', displayName: 'Erika' })
    const user = userEvent.setup()
    renderLoginFrom(from)
    await user.type(await screen.findByLabelText('E-Mail-Adresse'), 'user@example.test')
    await user.type(screen.getByLabelText('Passwort'), 'secret')
    await user.click(screen.getByRole('button', { name: 'Anmelden' }))
    expect(await screen.findByText('Ziel: /')).toBeInTheDocument()
  })
})

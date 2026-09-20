import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
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

describe('LoginPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    auth.refreshCsrfToken.mockResolvedValue(undefined)
    auth.me.mockRejectedValue(new ApiError(401))
  })

  it('masks the password and provides browser autofill hints', async () => {
    renderLogin()
    const email = await screen.findByLabelText('Email address')
    const password = screen.getByLabelText('Password')
    expect(email).toHaveAttribute('autocomplete', 'username')
    expect(password).toHaveAttribute('type', 'password')
    expect(password).toHaveAttribute('autocomplete', 'current-password')
  })

  it('preserves the exact password and announces generic credential errors', async () => {
    auth.login.mockRejectedValue(new ApiError(401))
    const user = userEvent.setup()
    renderLogin()
    await user.type(await screen.findByLabelText('Email address'), 'user@example.test')
    await user.type(screen.getByLabelText('Password'), '  Exact Case  ')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(auth.login).toHaveBeenCalledWith({ email: 'user@example.test', password: '  Exact Case  ' })
    expect(await screen.findByRole('alert')).toHaveTextContent('Email address or password is incorrect.')
  })

  it('shows the per-email cooldown, clears the password and leaves other email keys usable', async () => {
    auth.login.mockRejectedValue(Object.assign(new ApiError(401), { retryAfterSeconds: 900 }))
    const user = userEvent.setup()
    renderLogin()
    const email = await screen.findByLabelText('Email address')
    const password = screen.getByLabelText('Password')
    await user.type(email, 'user@example.test')
    await user.type(password, 'wrong')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(await screen.findByRole('status')).toHaveTextContent('15:00')
    expect(password).toHaveValue('')
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeDisabled()
    await user.clear(email)
    await user.type(email, 'another@example.test')
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeEnabled()
  })

  it('does not retry automatically when a cooldown expires', async () => {
    auth.login.mockRejectedValueOnce(Object.assign(new ApiError(429), { retryAfterSeconds: 1 }))
      .mockRejectedValueOnce(new ApiError(401))
    const user = userEvent.setup()
    renderLogin()
    await user.type(await screen.findByLabelText('Email address'), 'user@example.test')
    await user.type(screen.getByLabelText('Password'), 'wrong')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))
    await waitFor(() => expect(screen.getByRole('button', { name: 'Sign in' })).toBeEnabled(), { timeout: 2500 })
    expect(auth.login).toHaveBeenCalledTimes(1)
    await user.type(screen.getByLabelText('Password'), 'wrong-again')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(auth.login).toHaveBeenCalledTimes(2)
  })
})

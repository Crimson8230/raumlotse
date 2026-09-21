import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider } from './AuthProvider'
import { useAuth } from './useAuth'
import { ApiError } from '../API/client'

const auth = vi.hoisted(() => ({ me: vi.fn(), login: vi.fn(), refreshCsrfToken: vi.fn() }))
vi.mock('../API/auth', () => ({ authApi: auth }))
vi.mock('../API/client', () => ({ ApiError: class ApiError extends Error { readonly status: number; constructor(status: number) { super(); this.status = status } } }))

function State() {
  const { state, user, login, retryAvailability } = useAuth()
  return <><p>{state}{user ? `:${user.displayName}` : ''}</p>
    <button onClick={() => void login({ email: 'user@example.test', password: 'password' }).catch(() => undefined)}>login</button>
    <button onClick={() => void retryAvailability().catch(() => undefined)}>retry</button></>
}

describe('AuthProvider', () => {
  beforeEach(() => vi.clearAllMocks())

  it('publishes identity only after me and csrf bootstrap succeed', async () => {
    auth.refreshCsrfToken.mockResolvedValue(undefined)
    auth.me.mockResolvedValue({ userId: 'user-1', displayName: 'A User' })
    render(<AuthProvider><State /></AuthProvider>)
    expect(await screen.findByText('authenticated:A User')).toBeInTheDocument()
  })

  it('reports unavailable when bootstrap cannot reach the auth service', async () => {
    auth.refreshCsrfToken.mockRejectedValue(new Error('offline'))
    render(<AuthProvider><State /></AuthProvider>)
    expect(await screen.findByText('unavailable')).toBeInTheDocument()
  })

  it('retries post-login CSRF setup without resubmitting credentials', async () => {
    auth.refreshCsrfToken.mockResolvedValueOnce(undefined).mockRejectedValueOnce(new Error('temporary'))
      .mockResolvedValueOnce(undefined)
    auth.me.mockRejectedValue(new ApiError(401))
    auth.login.mockResolvedValue({ userId: 'user-1', displayName: 'A User' })
    const user = userEvent.setup()
    render(<AuthProvider><State /></AuthProvider>)
    await screen.findByText('anonymous')
    await user.click(screen.getByRole('button', { name: 'login' }))
    expect(await screen.findByText('unavailable:A User')).toBeInTheDocument()
    auth.me.mockResolvedValue({ userId: 'user-1', displayName: 'A User' })
    await user.click(screen.getByRole('button', { name: 'retry' }))
    expect(await screen.findByText('authenticated:A User')).toBeInTheDocument()
    expect(auth.login).toHaveBeenCalledTimes(1)
    expect(auth.me).toHaveBeenCalledTimes(2)
  })

  it('recovers the actual identity when another tab already signed in', async () => {
    auth.refreshCsrfToken.mockResolvedValue(undefined)
    auth.me.mockRejectedValueOnce(new ApiError(401)).mockResolvedValue({ userId: 'other', displayName: 'Other account' })
    auth.login.mockRejectedValue(new ApiError(409))
    render(<AuthProvider><State /></AuthProvider>)
    await screen.findByText('anonymous')
    await userEvent.click(screen.getByRole('button', { name: 'login' }))
    await screen.findByText('authenticated:Other account')
    expect(auth.login).toHaveBeenCalledTimes(1)
  })

  it('discards cached identity when recovery finds the session expired', async () => {
    auth.refreshCsrfToken.mockResolvedValueOnce(undefined).mockRejectedValueOnce(new Error('offline')).mockResolvedValue(undefined)
    auth.me.mockRejectedValue(new ApiError(401))
    auth.login.mockResolvedValue({ userId: 'user-1', displayName: 'A User' })
    render(<AuthProvider><State /></AuthProvider>)
    await screen.findByText('anonymous')
    await userEvent.click(screen.getByRole('button', { name: 'login' }))
    await screen.findByText('unavailable:A User')
    await userEvent.click(screen.getByRole('button', { name: 'retry' }))
    await screen.findByText('anonymous')
    expect(auth.login).toHaveBeenCalledTimes(1)
  })
})

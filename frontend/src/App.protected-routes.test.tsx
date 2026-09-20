import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import userEvent from '@testing-library/user-event'
import { ApiError } from './API/client'
import App from './App'

const auth = vi.hoisted(() => ({ me: vi.fn(), login: vi.fn(), refreshCsrfToken: vi.fn() }))
vi.mock('./API/auth', () => ({ authApi: auth }))
vi.mock('./API/health', () => ({ getHealth: vi.fn().mockResolvedValue({ status: 'ok' }) }))
vi.mock('./API/client', () => ({
  ApiError: class ApiError extends Error { readonly status: number; constructor(status: number) { super(); this.status = status } },
  clearCsrfToken: vi.fn(),
}))

describe('protected routes', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    auth.refreshCsrfToken.mockResolvedValue(undefined)
    auth.me.mockRejectedValue(new ApiError(401))
  })

  it('redirects direct protected URLs to login without mounting private pages', async () => {
    render(<MemoryRouter initialEntries={['/rooms/new']}><App /></MemoryRouter>)
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(screen.queryByText('Create room')).not.toBeInTheDocument()
    expect(screen.queryByRole('navigation')).not.toBeInTheDocument()
  })

  it('shows only loading status until session identity is resolved', async () => {
    let rejectMe!: (error: Error) => void
    auth.me.mockReturnValue(new Promise((_, reject) => { rejectMe = reject }))
    render(<MemoryRouter initialEntries={['/']}><App /></MemoryRouter>)
    expect(screen.getByText('Checking your sign-in…')).toBeInTheDocument()
    expect(screen.queryByRole('navigation')).not.toBeInTheDocument()
    rejectMe(new ApiError(401))
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
  })

  it('clears mounted protected content after a protected API 401', async () => {
    auth.me.mockResolvedValue({ userId: 'user-1', displayName: 'A User' })
    render(<MemoryRouter initialEntries={['/']}><App /></MemoryRouter>)
    expect(await screen.findByRole('navigation')).toBeInTheDocument()
    window.dispatchEvent(new Event('raumlotse:auth-expired'))
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(screen.queryByRole('navigation')).not.toBeInTheDocument()
    expect(screen.queryByText('Raumlotse')).not.toBeInTheDocument()
  })

  it('navigates to the authenticated home only after login and CSRF refresh succeed', async () => {
    auth.login.mockResolvedValue({ userId: 'user-1', displayName: 'A User' })
    const user = userEvent.setup()
    render(<MemoryRouter initialEntries={['/login']}><App /></MemoryRouter>)
    await user.type(await screen.findByLabelText('Email address'), 'user@example.test')
    await user.type(screen.getByLabelText('Password'), 'password')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(await screen.findByRole('navigation')).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: 'Raumlotse' })).toBeInTheDocument()
    expect(auth.login).toHaveBeenCalledWith({ email: 'user@example.test', password: 'password' })
    expect(auth.refreshCsrfToken).toHaveBeenCalledTimes(2)
  })
})

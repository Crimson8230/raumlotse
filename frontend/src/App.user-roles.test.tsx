import { act, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import * as roles from './API/userRoles'
import { ApiError } from './API/client'
const auth = vi.hoisted(() => ({ me: vi.fn(), login: vi.fn(), refreshCsrfToken: vi.fn() }))
vi.mock('./API/auth', () => ({ authApi: auth }))
vi.mock('./API/userRoles')
vi.mock('./API/health', () => ({ getHealth: vi.fn().mockResolvedValue({ status: 'ok' }) }))
beforeEach(() => {
  vi.resetAllMocks()
  auth.me.mockResolvedValue({ userId: 'admin', displayName: 'Admin' })
  auth.refreshCsrfToken.mockResolvedValue(undefined)
  vi.mocked(roles.getCurrentRoles).mockResolvedValue({ roles: ['ADMIN'], ready: true })
  vi.mocked(roles.listUsers).mockResolvedValue({ items: [], page: 0, size: 25, totalElements: 0 })
})
describe('role routes and navigation', () => {
  it('allows Admin deep links and exposes the navigation link', async () => {
    render(<MemoryRouter initialEntries={['/admin/users']}><App /></MemoryRouter>)
    await screen.findByRole('heading', { name: 'Benutzerrollen' })
    expect(screen.getByRole('link', { name: 'Benutzerrollen' })).toBeVisible()
  })
  it('denies University Staff and hides role navigation', async () => {
    vi.mocked(roles.getCurrentRoles).mockResolvedValue({ roles: ['UNIVERSITY_STAFF'], ready: true })
    render(<MemoryRouter initialEntries={['/admin/users/target/roles']}><App /></MemoryRouter>)
    await screen.findByText('Kein Zugriff auf die Rollenverwaltung.')
    expect(screen.queryByRole('link', { name: 'Benutzerrollen' })).not.toBeInTheDocument()
    expect(roles.getUserRoles).not.toHaveBeenCalled()
  })
  it('refreshes granted and revoked membership without another sign-in', async () => {
    vi.mocked(roles.getCurrentRoles).mockResolvedValue({ roles: ['VIEWER'], ready: true })
    render(<MemoryRouter initialEntries={['/admin/users']}><App /></MemoryRouter>)
    await screen.findByText('Kein Zugriff auf die Rollenverwaltung.')
    vi.mocked(roles.getCurrentRoles).mockResolvedValue({ roles: ['ADMIN'], ready: true })
    act(() => window.dispatchEvent(new Event('focus')))
    await screen.findByRole('heading', { name: 'Benutzerrollen' })
    vi.mocked(roles.getCurrentRoles).mockResolvedValue({ roles: ['VIEWER'], ready: true })
    act(() => window.dispatchEvent(new Event('raumlotse:roles-changed')))
    await screen.findByText('Kein Zugriff auf die Rollenverwaltung.')
    await waitFor(() => expect(screen.queryByRole('link', { name: 'Benutzerrollen' })).not.toBeInTheDocument())
  })
  it('uses the existing sign-in flow for an expired identity', async () => {
    auth.me.mockRejectedValue(new ApiError(401))
    render(<MemoryRouter initialEntries={['/admin/users']}><App /></MemoryRouter>)
    await screen.findByRole('heading', { name: 'Sign in' })
    expect(roles.listUsers).not.toHaveBeenCalled()
  })
})


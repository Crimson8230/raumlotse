import { render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import HomePage from './HomePage'
import * as healthApi from '../API/health'
import * as reservationsApi from '../API/reservations'

vi.mock('../API/health')
vi.mock('../API/reservations')
vi.mock('../auth/useCurrentRoles', () => ({ useCurrentRoles: () => ({
  loading: false, failed: mockRolesFailed,
  permissions: mockAuthState.state === 'authenticated' ? mockPermissions : [],
}) }))

let mockPermissions: string[] = ['READ']
let mockRolesFailed = false
let mockAuthState = {
  state: 'anonymous',
  user: null as { userId: string; displayName: string } | null,
}

vi.mock('../auth/useAuth', () => ({
  useAuth: () => mockAuthState,
}))

const health = vi.mocked(healthApi)
const reservations = vi.mocked(reservationsApi)

describe('HomePage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    mockAuthState = {
      state: 'anonymous',
      user: null,
    }
    mockPermissions = ['READ']
    mockRolesFailed = false
    health.getHealth.mockResolvedValue({ status: 'UP' })
    reservations.getMyUpcomingReservations.mockResolvedValue([])
  })

  it('shows the pending health check with the loading status class', () => {
    health.getHealth.mockReturnValue(new Promise(() => {}))

    render(
      <MemoryRouter>
        <HomePage />
      </MemoryRouter>,
    )

    expect(screen.getByText('checking...')).toHaveClass('status-loading')
  })

  it('shows an unreachable backend with the semantic error status class', async () => {
    health.getHealth.mockRejectedValue(new Error('network error'))

    render(
      <MemoryRouter>
        <HomePage />
      </MemoryRouter>,
    )

    expect(await screen.findByText('unreachable')).toHaveClass('status-unreachable')
  })

  it('omits MyUpcomingReservations from the DOM when visitor is unauthenticated', () => {
    mockAuthState = {
      state: 'anonymous',
      user: null,
    }

    render(
      <MemoryRouter>
        <HomePage />
      </MemoryRouter>,
    )

    expect(screen.queryByRole('heading', { name: /meine nächsten reservierungen|my upcoming reservations/i })).not.toBeInTheDocument()
    expect(reservations.getMyUpcomingReservations).not.toHaveBeenCalled()
  })

  it('renders MyUpcomingReservations when user is authenticated', async () => {
    mockAuthState = {
      state: 'authenticated',
      user: { userId: 'u1', displayName: 'Jane Doe' },
    }

    render(
      <MemoryRouter>
        <HomePage />
      </MemoryRouter>,
    )

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /meine nächsten reservierungen|my upcoming reservations/i })).toBeInTheDocument()
    })
    expect(reservations.getMyUpcomingReservations).toHaveBeenCalled()
  })

  it('shows a clear empty state when no functions are granted', async () => {
    mockAuthState = { state: 'authenticated', user: { userId: 'u1', displayName: 'Viewer' } }
    mockPermissions = []
    render(<MemoryRouter><HomePage /></MemoryRouter>)
    expect(screen.getByRole('status')).toHaveTextContent('keine Funktionen freigegeben')
    expect(reservations.getMyUpcomingReservations).not.toHaveBeenCalled()
  })

  it('does not show reservation data after a permission lookup fails', () => {
    mockAuthState = { state: 'authenticated', user: { userId: 'u1', displayName: 'Viewer' } }
    mockPermissions = []
    mockRolesFailed = true
    render(<MemoryRouter><HomePage /></MemoryRouter>)
    expect(reservations.getMyUpcomingReservations).not.toHaveBeenCalled()
    expect(screen.queryByText(/keine Funktionen freigegeben/)).not.toBeInTheDocument()
  })
})

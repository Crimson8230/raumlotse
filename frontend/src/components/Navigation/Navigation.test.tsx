import { MemoryRouter } from 'react-router-dom'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { Navigation } from './Navigation'

const mode = vi.hoisted(() => ({
  state: { loading: false, failed: false, admin: false, adminMode: false },
  eligible: undefined as boolean | undefined,
  permissions: ['READ', 'BUILDING_MANAGE', 'MAP_MANAGE', 'STATISTICS_READ'] as string[],
  setMode: vi.fn(),
}))
vi.mock('../../auth/useAdminMode', () => ({ useAdminMode: () => ({ ...mode.state,
  canUseAdminMode: mode.eligible ?? mode.state.admin,
  permissions: mode.permissions,
  setMode: mode.setMode }) }))

function renderNav(initialPath: string) {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <Navigation />
    </MemoryRouter>,
  )
}

beforeEach(() => {
  mode.state = { loading: false, failed: false, admin: false, adminMode: false }
  mode.eligible = undefined
  mode.permissions = ['READ', 'BUILDING_MANAGE', 'MAP_MANAGE', 'STATISTICS_READ']
  mode.setMode.mockReset().mockResolvedValue(undefined)
})

describe('Navigation', () => {
  it('always offers personal settings, including users without READ', () => {
    mode.permissions = []
    renderNav('/')
    expect(screen.getByRole('link', { name: 'Persönliche Einstellungen' })).toHaveAttribute('href', '/settings')
  })
  it('offers a staff member with one management right only the matching admin area', async () => {
    mode.eligible = true
    mode.permissions = ['READ', 'BUILDING_MANAGE']
    const view = renderNav('/')
    await userEvent.click(screen.getByRole('button', { name: 'Administrationsmodus' }))
    expect(mode.setMode).toHaveBeenCalledWith(true)
    view.unmount()
    mode.state.adminMode = true
    renderNav('/')
    expect(screen.getByRole('link', { name: 'Standorte' })).toBeVisible()
    expect(screen.queryByRole('link', { name: 'Karten bearbeiten' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Statistiken' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Belegung' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Benutzerrollen' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Rollenrechte' })).not.toBeInTheDocument()
  })
  it('shows the user view links only, without any mode switch, to regular users', () => {
    renderNav('/')

    expect(screen.getByRole('link', { name: 'Home' })).toHaveAttribute('href', '/')
    expect(screen.getByRole('link', { name: 'Räume' })).toHaveAttribute('href', '/rooms')
    expect(screen.getByRole('link', { name: 'Karten' })).toHaveAttribute('href', '/maps')
    expect(screen.queryByRole('link', { name: 'Standorte' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Benutzerrollen' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Administrationsmodus/ })).not.toBeInTheDocument()
  })

  it('offers administrators the mode switch but keeps administration links hidden while it is off', async () => {
    const user = userEvent.setup()
    mode.state.admin = true
    renderNav('/')

    expect(screen.queryByRole('link', { name: 'Standorte' })).not.toBeInTheDocument()
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Administrationsmodus' }))

    expect(mode.setMode).toHaveBeenCalledWith(true)
  })

  it('shows administration links and a persistent indicator while the mode is on and ends it in one click', async () => {
    const user = userEvent.setup()
    mode.state.admin = true
    mode.state.adminMode = true
    renderNav('/')

    expect(screen.getByRole('link', { name: 'Standorte' })).toHaveAttribute('href', '/admin/locations')
    expect(screen.getByRole('link', { name: 'Karten bearbeiten' })).toHaveAttribute('href', '/admin/maps')
    expect(screen.getByRole('link', { name: 'Belegung' })).toHaveAttribute('href', '/admin/occupancy')
    expect(screen.getByRole('link', { name: 'Benutzerrollen' })).toHaveAttribute('href', '/admin/users')
    expect(screen.getByRole('link', { name: 'Systemeinstellungen' })).toHaveAttribute('href', '/admin/settings')
    expect(screen.getByRole('status')).toHaveTextContent('Administrationsmodus aktiv')
    await user.click(screen.getByRole('button', { name: 'Administrationsmodus beenden' }))

    expect(mode.setMode).toHaveBeenCalledWith(false)
  })

  it('inverts the whole bar while administration mode is on and not otherwise', () => {
    mode.state.admin = true
    const { rerender } = renderNav('/')
    expect(screen.getByRole('navigation', { name: 'Hauptnavigation' })).not.toHaveClass('nav-admin')

    mode.state.adminMode = true
    rerender(
      <MemoryRouter initialEntries={['/']}>
        <Navigation />
      </MemoryRouter>,
    )

    expect(screen.getByRole('navigation', { name: 'Hauptnavigation' })).toHaveClass('nav-admin')
  })

  it('reports a failed mode change', async () => {
    const user = userEvent.setup()
    mode.state.admin = true
    mode.setMode.mockRejectedValue(new Error('boom'))
    renderNav('/')

    await user.click(screen.getByRole('button', { name: 'Administrationsmodus' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('boom')
  })

  it('marks the matching top-level item active for a nested route, and no other item', () => {
    renderNav('/rooms/some-id')

    expect(screen.getByRole('link', { name: 'Räume' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('link', { name: 'Home' })).not.toHaveAttribute('aria-current')
  })

  it('activates the clicked link, deactivating the previous one', async () => {
    const user = userEvent.setup()
    renderNav('/')

    expect(screen.getByRole('link', { name: 'Home' })).toHaveAttribute('aria-current', 'page')

    await user.click(screen.getByRole('link', { name: 'Räume' }))

    expect(screen.getByRole('link', { name: 'Räume' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('link', { name: 'Home' })).not.toHaveAttribute('aria-current')
  })
})

describe('Navigation layout (feature 019, FR-015)', () => {
  it('allows the four user links to wrap at a 320 px viewport', async () => {
    const { readFileSync } = await import('node:fs')
    const { join } = await import('node:path')
    const style = document.createElement('style')
    style.textContent = readFileSync(join(__dirname, 'Navigation.css'), 'utf8')
    document.head.append(style)
    try {
      mode.permissions = ['READ']
      const { container } = renderNav('/settings')
      const links = container.querySelector('.nav-list') as HTMLElement
      expect(screen.getByRole('link', { name: 'Persönliche Einstellungen' })).toBeInTheDocument()
      expect(getComputedStyle(links).flexWrap).toBe('wrap')
    } finally {
      style.remove()
    }
  })
  it('keeps the administration indicator and switch from wrapping internally', async () => {
    const { readFileSync } = await import('node:fs')
    const { join } = await import('node:path')
    const css = readFileSync(join(__dirname, 'Navigation.css'), 'utf8')
    expect(css).toMatch(/\.nav-admin-indicator,\s*\.nav-admin-mode button\s*\{\s*white-space:\s*nowrap/)
    expect(css).toMatch(/\.nav-admin-mode\s*\{[^}]*flex:\s*0 0 auto/)
  })

  it('shows all admin links and the indicator in one navigation landmark', () => {
    mode.state = { loading: false, failed: false, admin: true, adminMode: true }
    mode.eligible = true
    mode.permissions = ['READ', 'BUILDING_MANAGE', 'MAP_MANAGE', 'STATISTICS_READ']
    renderNav('/admin/locations')
    const nav = screen.getByRole('navigation', { name: 'Hauptnavigation' })
    expect(within(nav).getAllByRole('link').length).toBeGreaterThanOrEqual(8)
    expect(within(nav).getByRole('status')).toHaveTextContent('Administrationsmodus aktiv')
  })
})

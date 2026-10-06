import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import * as roles from './API/userRoles'
import { ApiError } from './API/client'
const auth = vi.hoisted(() => ({ me: vi.fn(), login: vi.fn(), refreshCsrfToken: vi.fn() }))
vi.mock('./API/auth', () => ({ authApi: auth }))
vi.mock('./API/userRoles')
vi.mock('./API/buildings', () => ({ listBuildings: vi.fn().mockResolvedValue([]), listFloors: vi.fn().mockResolvedValue([]) }))
vi.mock('./API/equipmentTypes', () => ({ listEquipmentTypes: vi.fn().mockResolvedValue([]) }))
vi.mock('./API/health', () => ({ getHealth: vi.fn().mockResolvedValue({ status: 'ok' }) }))

const admin = { roles: ['ADMIN'], ready: true, adminMode: true } as const
const adminModeOff = { roles: ['ADMIN'], ready: true, adminMode: false } as const
const viewer = { roles: ['VIEWER'], ready: true, adminMode: false } as const

function open(path: string) {
  return render(<MemoryRouter initialEntries={[path]}><App /></MemoryRouter>)
}

beforeEach(() => {
  vi.resetAllMocks()
  auth.me.mockResolvedValue({ userId: 'admin', displayName: 'Administration' })
  auth.refreshCsrfToken.mockResolvedValue(undefined)
  vi.mocked(roles.getCurrentRoles).mockResolvedValue({ ...admin, roles: [...admin.roles] })
  vi.mocked(roles.listUsers).mockResolvedValue({ items: [], page: 0, size: 25, totalElements: 0 })
})

describe('administration routes and mode', () => {
  it('lets an administrator with administration mode on open an administration address and shows its navigation', async () => {
    open('/admin/users')
    await screen.findByRole('heading', { name: 'Benutzerrollen' })
    expect(screen.getByRole('link', { name: 'Benutzerrollen' })).toBeVisible()
    expect(await screen.findByText('Administrationsmodus aktiv')).toBeVisible()
  })

  it('denies a regular user, hides administration navigation and loads nothing', async () => {
    vi.mocked(roles.getCurrentRoles).mockResolvedValue({ ...viewer, roles: [...viewer.roles] })
    open('/admin/users/target/roles')
    await screen.findByText('Diese Seite ist für Sie nicht verfügbar.')
    expect(screen.queryByRole('link', { name: 'Benutzerrollen' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Administrationsmodus/ })).not.toBeInTheDocument()
    expect(roles.getUserRoles).not.toHaveBeenCalled()
  })

  it('offers an administrator with the mode off to switch it on instead of showing the page', async () => {
    const user = userEvent.setup()
    vi.mocked(roles.getCurrentRoles).mockResolvedValue({ ...adminModeOff, roles: [...adminModeOff.roles] })
    vi.mocked(roles.setAdminMode).mockResolvedValue({ adminMode: true })
    open('/admin/users')
    await screen.findByText('Diese Seite gehört zum Administrationsmodus.')
    expect(roles.listUsers).not.toHaveBeenCalled()

    vi.mocked(roles.getCurrentRoles).mockResolvedValue({ ...admin, roles: [...admin.roles] })
    await user.click(screen.getByRole('button', { name: 'Administrationsmodus aktivieren' }))

    expect(roles.setAdminMode).toHaveBeenCalledWith(true)
    await screen.findByRole('heading', { name: 'Benutzerrollen' })
  })

  it('leaves administration pages when the administrator ends the mode', async () => {
    const user = userEvent.setup()
    vi.mocked(roles.setAdminMode).mockResolvedValue({ adminMode: false })
    open('/admin/users')
    await screen.findByRole('heading', { name: 'Benutzerrollen' })

    vi.mocked(roles.getCurrentRoles).mockResolvedValue({ ...adminModeOff, roles: [...adminModeOff.roles] })
    await user.click(screen.getByRole('button', { name: 'Administrationsmodus beenden' }))

    expect(roles.setAdminMode).toHaveBeenCalledWith(false)
    await screen.findByText('Diese Seite gehört zum Administrationsmodus.')
    expect(screen.queryByRole('link', { name: 'Benutzerrollen' })).not.toBeInTheDocument()
  })

  it('refreshes granted and revoked membership without another sign-in', async () => {
    vi.mocked(roles.getCurrentRoles).mockResolvedValue({ ...viewer, roles: [...viewer.roles] })
    open('/admin/users')
    await screen.findByText('Diese Seite ist für Sie nicht verfügbar.')
    vi.mocked(roles.getCurrentRoles).mockResolvedValue({ ...admin, roles: [...admin.roles] })
    act(() => window.dispatchEvent(new Event('focus')))
    await screen.findByRole('heading', { name: 'Benutzerrollen' })
    vi.mocked(roles.getCurrentRoles).mockResolvedValue({ ...viewer, roles: [...viewer.roles] })
    act(() => window.dispatchEvent(new Event('raumlotse:roles-changed')))
    await screen.findByText('Diese Seite ist für Sie nicht verfügbar.')
    await waitFor(() => expect(screen.queryByRole('link', { name: 'Benutzerrollen' })).not.toBeInTheDocument())
  })

  it('forwards the addresses from before the split to their administration equivalents', async () => {
    open('/locations')
    await screen.findByRole('heading', { name: 'Standorte' })
  })

  it('does not show the forwarded administration page to a regular user', async () => {
    vi.mocked(roles.getCurrentRoles).mockResolvedValue({ ...viewer, roles: [...viewer.roles] })
    open('/rooms/room-1/edit')
    await screen.findByText('Diese Seite ist für Sie nicht verfügbar.')
  })

  it('keeps the user view free of administration links', async () => {
    vi.mocked(roles.getCurrentRoles).mockResolvedValue({ ...viewer, roles: [...viewer.roles] })
    open('/maps')
    await screen.findByRole('navigation', { name: 'Hauptnavigation' })
    expect(screen.getByRole('link', { name: 'Räume' })).toBeVisible()
    expect(screen.queryByRole('link', { name: 'Standorte' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Karten bearbeiten' })).not.toBeInTheDocument()
  })

  it('uses the existing sign-in flow for an expired identity', async () => {
    auth.me.mockRejectedValue(new ApiError(401))
    open('/admin/users')
    await screen.findByRole('heading', { name: 'Anmelden' })
    expect(roles.listUsers).not.toHaveBeenCalled()
  })
})

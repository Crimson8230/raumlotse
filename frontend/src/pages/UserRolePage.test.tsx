import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../API/client'
import * as api from '../API/userRoles'
import UserRolePage from './UserRolePage'
vi.mock('../API/userRoles')
vi.mock('../auth/useAuth', () => ({ useAuth: () => ({ user: { userId: 'admin' } }) }))
const initial = { user: { id: 'target', displayName: 'Target User', accountLabel: 'target@example.test' }, roles: ['VIEWER'] as const, version: '0', availableRoles: [] }
function data(roles: ('VIEWER' | 'STUDENT' | 'LECTURER')[] = ['VIEWER'], version = '0') { return { ...initial, roles, version } }
function renderPage() {
  render(<MemoryRouter initialEntries={['/admin/users/target/roles']}><Routes>
    <Route path="/admin/users/:userId/roles" element={<UserRolePage />} />
    <Route path="/admin/users" element={<p>User list</p>} />
    <Route path="/" element={<p>Home</p>} />
  </Routes></MemoryRouter>)
}
beforeEach(() => { vi.resetAllMocks(); vi.mocked(api.getUserRoles).mockResolvedValue(data()) })
describe('role management page', () => {
  it('saves a replacement and shows the committed selection', async () => {
    vi.mocked(api.saveUserRoles).mockResolvedValue(data(['STUDENT'], '1'))
    renderPage()
    await screen.findByText('Target User')
    await userEvent.click(screen.getByRole('checkbox', { name: 'Student' }))
    await userEvent.click(screen.getByRole('checkbox', { name: 'Viewer' }))
    await userEvent.click(screen.getByRole('button', { name: 'Speichern' }))
    await screen.findByText('Rollen gespeichert.')
    expect(api.saveUserRoles).toHaveBeenCalledWith('target', ['STUDENT'], '0')
    expect(screen.getByRole('checkbox', { name: 'Student' })).toBeChecked()
    expect(screen.getByRole('checkbox', { name: 'Viewer' })).not.toBeChecked()
  })
  it('requires explicit review after conflict before a new save', async () => {
    vi.mocked(api.saveUserRoles).mockRejectedValueOnce(new ApiError(409, { title: 'Conflict', status: 409, detail: 'changed', errors: [], code: 'STALE_ROLES' }))
    renderPage()
    await screen.findByText('Target User')
    await userEvent.click(screen.getByRole('checkbox', { name: 'Student' }))
    await userEvent.click(screen.getByRole('button', { name: 'Speichern' }))
    const review = await screen.findByRole('button', { name: 'Aktuelle Rollen prüfen' })
    expect(screen.getByRole('button', { name: 'Speichern' })).toBeDisabled()
    expect(api.getUserRoles).toHaveBeenCalledTimes(1)
    vi.mocked(api.getUserRoles).mockResolvedValue(data(['LECTURER'], '2'))
    await userEvent.click(review)
    await waitFor(() => expect(screen.getByRole('checkbox', { name: 'Lecturer' })).toBeChecked())
    expect(screen.getByRole('checkbox', { name: 'Student' })).not.toBeChecked()
    vi.mocked(api.saveUserRoles).mockResolvedValue(data(['LECTURER', 'STUDENT'], '3'))
    await userEvent.click(screen.getByRole('checkbox', { name: 'Student' }))
    await userEvent.click(screen.getByRole('button', { name: 'Speichern' }))
    await screen.findByText('Rollen gespeichert.')
    expect(api.saveUserRoles).toHaveBeenLastCalledWith('target', ['LECTURER', 'STUDENT'], '2')
  })
  it('cancels without sending any write', async () => {
    renderPage(); await screen.findByText('Target User')
    await userEvent.click(screen.getByRole('checkbox', { name: 'Student' }))
    await userEvent.click(screen.getByRole('button', { name: 'Abbrechen' }))
    await screen.findByText('User list')
    expect(api.saveUserRoles).not.toHaveBeenCalled()
  })
  it('requires refetch after a lost response and removes controls after denial', async () => {
    vi.mocked(api.saveUserRoles).mockRejectedValue(new TypeError('Network error'))
    renderPage(); await screen.findByText('Target User')
    await userEvent.click(screen.getByRole('button', { name: 'Speichern' }))
    await screen.findByRole('button', { name: 'Aktuelle Rollen prüfen' })
    expect(screen.getByRole('button', { name: 'Speichern' })).toBeDisabled()
    vi.mocked(api.getUserRoles).mockRejectedValue(new ApiError(403))
    await userEvent.click(screen.getByRole('button', { name: 'Aktuelle Rollen prüfen' }))
    await screen.findByText('Kein Zugriff auf die Rollenverwaltung.')
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument()
  })
})


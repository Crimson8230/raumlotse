import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import RolePermissionPage from './RolePermissionPage'
import * as api from '../API/rolePermissions'
import { ApiError } from '../API/client'
import { permissionCodes } from '../test/permissionFixtures'
import type { RolePermissions } from '../types/permission'

vi.mock('../API/rolePermissions')

const role: RolePermissions = {
  role: { code: 'ADMIN', label: 'Administration' },
  permissions: ['READ'], version: '4', protectedRoleManagement: true,
  availablePermissions: permissionCodes.map(code => ({ code, label: code, description: `${code} nutzen` })),
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(api.listRoleOptions).mockResolvedValue({ items: [{ code: 'ADMIN', label: 'Administration' }] })
  vi.mocked(api.getRolePermissions).mockResolvedValue(role)
})

it('shows all 14 choices and the unchangeable Admin privilege', async () => {
  render(<MemoryRouter><RolePermissionPage /></MemoryRouter>)
  expect(await screen.findByText(/fest mit der Admin-Rolle verbunden/)).toBeVisible()
  expect(screen.getAllByRole('checkbox')).toHaveLength(14)
  expect(screen.getAllByRole('checkbox')[0]).toBeChecked()
})

it('saves a complete versioned selection and makes conflicts require a reload', async () => {
  const user = userEvent.setup()
  vi.mocked(api.saveRolePermissions).mockResolvedValueOnce({ ...role, permissions: ['READ', 'BUILDING_MANAGE'], version: '5' })
    .mockRejectedValueOnce(new ApiError(409, { title: 'Conflict', status: 409, detail: 'Stale', code: 'STALE_ROLE_PERMISSIONS' }))
  render(<MemoryRouter><RolePermissionPage /></MemoryRouter>)
  await user.click(await screen.findByRole('checkbox', { name: /BUILDING_MANAGE nutzen/ }))
  await user.click(screen.getByRole('button', { name: 'Speichern' }))
  await waitFor(() => expect(api.saveRolePermissions).toHaveBeenCalledWith('ADMIN',
    ['READ', 'BUILDING_MANAGE'], '4'))
  await user.click(screen.getByRole('button', { name: 'Speichern' }))
  expect(await screen.findByRole('button', { name: 'Aktuellen Stand laden' })).toBeVisible()
  expect(screen.getByRole('button', { name: 'Speichern' })).toBeDisabled()
})

it('lets an administrator inspect each fixed role and cancel a draft', async () => {
  const user = userEvent.setup()
  const codes = ['ADMIN', 'UNIVERSITY_STAFF', 'STUDENT', 'LECTURER', 'VIEWER'] as const
  vi.mocked(api.listRoleOptions).mockResolvedValue({ items: codes.map(code => ({ code, label: code })) })
  vi.mocked(api.getRolePermissions).mockImplementation(async code => ({
    ...role, role: { code, label: code }, protectedRoleManagement: code === 'ADMIN',
  }))
  render(<MemoryRouter><RolePermissionPage /></MemoryRouter>)
  const selector = await screen.findByRole('combobox', { name: 'Rolle' })
  expect(screen.getAllByRole('option')).toHaveLength(5)
  await user.selectOptions(selector, 'VIEWER')
  expect(await screen.findByText('Funktionen für VIEWER')).toBeVisible()
  expect(screen.getByText(/fest mit der Admin-Rolle verbunden/)).toBeVisible()
  await user.click(screen.getByRole('checkbox', { name: /RESERVE nutzen/ }))
  await user.click(screen.getByRole('button', { name: 'Abbrechen' }))
  expect(screen.getByRole('checkbox', { name: /RESERVE nutzen/ })).not.toBeChecked()
  expect(api.saveRolePermissions).not.toHaveBeenCalled()
})

it.each(['UNIVERSITY_STAFF', 'STUDENT', 'LECTURER', 'VIEWER'] as const)(
  'shows fixed role management as unavailable while editing %s', async code => {
    const user = userEvent.setup()
    const codes = ['ADMIN', 'UNIVERSITY_STAFF', 'STUDENT', 'LECTURER', 'VIEWER'] as const
    vi.mocked(api.listRoleOptions).mockResolvedValue({ items: codes.map(item => ({ code: item, label: item })) })
    vi.mocked(api.getRolePermissions).mockImplementation(async roleCode => ({
      ...role, role: { code: roleCode, label: roleCode }, protectedRoleManagement: roleCode === 'ADMIN',
    }))
    render(<MemoryRouter><RolePermissionPage /></MemoryRouter>)
    await user.selectOptions(await screen.findByRole('combobox', { name: 'Rolle' }), code)
    expect(await screen.findByText(/Rollen und Rollenrechte verwalten: fest mit der Admin-Rolle verbunden/)).toBeVisible()
    expect(screen.getByText(/nicht verf.*und nicht änderbar/)).toBeVisible()
    expect(screen.queryByRole('checkbox', { name: /protectedRoleManagement/i })).not.toBeInTheDocument()
  },
)

it('clears loaded rights when a reload fails after a stale selection', async () => {
  const user = userEvent.setup()
  vi.mocked(api.saveRolePermissions).mockRejectedValueOnce(new ApiError(409, {
    title: 'Conflict', status: 409, detail: 'Stale', code: 'STALE_ROLE_PERMISSIONS',
  }))
  vi.mocked(api.getRolePermissions).mockResolvedValueOnce(role).mockRejectedValueOnce(new Error('reload failed'))
  render(<MemoryRouter><RolePermissionPage /></MemoryRouter>)
  await screen.findByRole('checkbox', { name: 'READ READ nutzen' })
  await user.click(screen.getByRole('button', { name: 'Speichern' }))
  await user.click(await screen.findByRole('button', { name: 'Aktuellen Stand laden' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('reload failed')
  expect(screen.queryAllByRole('checkbox')).toHaveLength(0)
  expect(screen.queryByRole('button', { name: 'Speichern' })).not.toBeInTheDocument()
})

it('blocks a selection that removes READ while another permission remains', async () => {
  const user = userEvent.setup()
  vi.mocked(api.getRolePermissions).mockResolvedValue({ ...role, permissions: ['READ', 'RESERVE'] })
  render(<MemoryRouter><RolePermissionPage /></MemoryRouter>)
  await user.click(await screen.findByRole('checkbox', { name: 'READ READ nutzen' }))
  expect(screen.getByText(/muss „Lesen“/)).toBeVisible()
  expect(screen.getByRole('button', { name: 'Speichern' })).toBeDisabled()
  expect(api.saveRolePermissions).not.toHaveBeenCalled()
})

it('shows a request error and allows reloading the selected role', async () => {
  const user = userEvent.setup()
  vi.mocked(api.getRolePermissions).mockRejectedValueOnce(new Error('Laden fehlgeschlagen'))
    .mockResolvedValueOnce(role)
  render(<MemoryRouter><RolePermissionPage /></MemoryRouter>)
  expect(await screen.findByRole('alert')).toHaveTextContent('Laden fehlgeschlagen')
  expect(screen.queryAllByRole('checkbox')).toHaveLength(0)
  await user.click(screen.getByRole('button', { name: 'Erneut laden' }))
  expect(await screen.findByRole('checkbox', { name: 'READ READ nutzen' })).toBeChecked()
})

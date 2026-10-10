import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import { permissionCodes } from '../test/permissionFixtures'
import { RequireRoleManagement } from './RequireRoleManagement'

let mockRoles = { loading: false, failed: false, admin: false, permissions: [] as string[] }
vi.mock('./useCurrentRoles', () => ({ useCurrentRoles: () => mockRoles }))

function renderGuard() {
  render(<MemoryRouter initialEntries={['/admin/roles']}><Routes>
    <Route element={<RequireRoleManagement />}>
      <Route path="/admin/roles" element={<h1>Rollenrechte verwalten</h1>} />
    </Route>
  </Routes></MemoryRouter>)
}

beforeEach(() => { mockRoles = { loading: false, failed: false, admin: false, permissions: [] } })

it('denies role management to a non-admin even with all configurable permissions', () => {
  mockRoles.permissions = [...permissionCodes]
  renderGuard()
  expect(screen.getByRole('alert')).toHaveTextContent('nur für Administratoren')
  expect(screen.queryByRole('heading', { name: 'Rollenrechte verwalten' })).not.toBeInTheDocument()
})

it('allows an administrator with no configurable permissions', () => {
  mockRoles.admin = true
  renderGuard()
  expect(screen.getByRole('heading', { name: 'Rollenrechte verwalten' })).toBeVisible()
})

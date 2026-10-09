import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import { RequireAdminMode } from './RequireAdminMode'

const mode = vi.hoisted(() => ({
  eligible: false, active: false, setMode: vi.fn(),
}))
vi.mock('./useAdminMode', () => ({ useAdminMode: () => ({
  loading: false, failed: false, canUseAdminMode: mode.eligible,
  adminMode: mode.active, setMode: mode.setMode,
}) }))

function renderRoute() {
  return render(<MemoryRouter initialEntries={['/admin/locations']}><Routes>
    <Route element={<RequireAdminMode />}>
      <Route path="/admin/locations" element={<h1>Standorte verwalten</h1>} />
    </Route>
  </Routes></MemoryRouter>)
}

beforeEach(() => {
  mode.eligible = false
  mode.active = false
  mode.setMode.mockReset().mockResolvedValue(undefined)
})

it('lets eligible staff activate administration mode without an Admin role', async () => {
  mode.eligible = true
  renderRoute()
  await userEvent.click(screen.getByRole('button', { name: 'Administrationsmodus aktivieren' }))
  expect(mode.setMode).toHaveBeenCalledWith(true)
})

it('shows the allowed page only while mode is active and eligibility remains', () => {
  mode.eligible = true
  mode.active = true
  const view = renderRoute()
  expect(screen.getByRole('heading', { name: 'Standorte verwalten' })).toBeVisible()
  mode.eligible = false
  view.rerender(<MemoryRouter initialEntries={['/admin/locations']}><Routes>
    <Route element={<RequireAdminMode />}>
      <Route path="/admin/locations" element={<h1>Standorte verwalten</h1>} />
    </Route>
  </Routes></MemoryRouter>)
  expect(screen.getByRole('alert')).toHaveTextContent('nicht verfügbar')
  expect(screen.queryByRole('heading', { name: 'Standorte verwalten' })).not.toBeInTheDocument()
})

import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import { getCurrentRoles } from '../API/userRoles'
import { RequirePermission } from './RequirePermission'

vi.mock('../API/userRoles', () => ({ getCurrentRoles: vi.fn() }))

type Snapshot = Awaited<ReturnType<typeof getCurrentRoles>>
const granted: Snapshot = { roles: ['ADMIN'], ready: true, adminMode: true,
  permissions: ['STATISTICS_READ'], canUseAdminMode: true }

function Counter() {
  const [count, setCount] = useState(0)
  return <button type="button" onClick={() => setCount(count + 1)}>Zähler {count}</button>
}

function renderRoute() {
  return render(<MemoryRouter initialEntries={['/stats']}><Routes>
    <Route element={<RequirePermission any={['STATISTICS_READ']} />}>
      <Route path="/stats" element={<Counter />} />
    </Route>
  </Routes></MemoryRouter>)
}

beforeEach(() => vi.resetAllMocks())

it('keeps the page mounted while permissions are re-checked after returning to the window', async () => {
  let finishRefresh: (value: Snapshot) => void = () => {}
  vi.mocked(getCurrentRoles).mockResolvedValueOnce(granted)
    .mockImplementationOnce(() => new Promise(resolve => { finishRefresh = resolve }))
  renderRoute()
  await userEvent.click(await screen.findByRole('button', { name: 'Zähler 0' }))

  act(() => window.dispatchEvent(new Event('focus')))
  expect(screen.getByRole('button', { name: 'Zähler 1' })).toBeVisible()
  await act(async () => finishRefresh(granted))
  expect(screen.getByRole('button', { name: 'Zähler 1' })).toBeVisible()
})

it('removes the page when the re-check revokes the right', async () => {
  vi.mocked(getCurrentRoles).mockResolvedValueOnce(granted)
    .mockResolvedValueOnce({ ...granted, permissions: ['READ'] })
  renderRoute()
  await screen.findByRole('button', { name: 'Zähler 0' })

  act(() => window.dispatchEvent(new Event('focus')))
  await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('nicht verfügbar'))
  expect(screen.queryByRole('button', { name: /Zähler/ })).not.toBeInTheDocument()
})

it('shows the check status instead of the page on the first check', () => {
  vi.mocked(getCurrentRoles).mockImplementationOnce(() => new Promise(() => {}))
  renderRoute()
  expect(screen.getByRole('status')).toHaveTextContent('Berechtigung wird geprüft')
  expect(screen.queryByRole('button', { name: /Zähler/ })).not.toBeInTheDocument()
})

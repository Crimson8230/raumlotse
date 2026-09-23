import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import * as api from '../API/userRoles'
import UserRoleListPage from './UserRoleListPage'
vi.mock('../API/userRoles')
beforeEach(() => vi.resetAllMocks())
it('searches and pages with identity labels', async () => {
  vi.mocked(api.listUsers).mockResolvedValue({ items: [{ id: 'one', displayName: 'Person', accountLabel: 'one@example.test' }], page: 0, size: 25, totalElements: 26 })
  render(<MemoryRouter><UserRoleListPage /></MemoryRouter>)
  await screen.findByText('one@example.test')
  await userEvent.click(screen.getByRole('button', { name: 'Weiter' }))
  expect(api.listUsers).toHaveBeenLastCalledWith('', 1)
  await userEvent.type(screen.getByRole('searchbox'), 'Person')
  await userEvent.click(screen.getByRole('button', { name: 'Suchen' }))
  expect(api.listUsers).toHaveBeenLastCalledWith('Person', 0)
})
it('provides retry and an empty state', async () => {
  vi.mocked(api.listUsers).mockRejectedValueOnce(new Error('offline')).mockResolvedValue({ items: [], page: 0, size: 25, totalElements: 0 })
  render(<MemoryRouter><UserRoleListPage /></MemoryRouter>)
  await userEvent.click(await screen.findByRole('button', { name: 'Erneut versuchen' }))
  await screen.findByText('Keine Benutzer gefunden.')
})


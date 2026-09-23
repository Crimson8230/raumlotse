import vectors from '../../../test-fixtures/login-emails.json'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import LoginPage from './LoginPage'

const login = vi.hoisted(() => vi.fn().mockResolvedValue(undefined))
vi.mock('../auth/useAuth', () => ({ useAuth: () => ({ state: 'anonymous', user: null, login }) }))
beforeEach(() => login.mockClear())
it.each(vectors)('shares provisioning validation for $input', async ({ input, canonical }) => {
  render(<MemoryRouter><LoginPage /></MemoryRouter>)
  const user = userEvent.setup()
  if (input) await user.type(screen.getByLabelText('Email address'), input)
  await user.type(screen.getByLabelText('Password'), ' Exact password ')
  await user.click(screen.getByRole('button', { name: 'Sign in' }))
  if (canonical) expect(login).toHaveBeenCalledOnce()
  else expect(login).not.toHaveBeenCalled()
})

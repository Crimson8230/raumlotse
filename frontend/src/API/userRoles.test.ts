import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getCurrentRoles, setAdminMode } from './userRoles'
import { apiRequest } from './client'

vi.mock('./client')

beforeEach(() => {
  vi.resetAllMocks()
})

describe('administration mode API', () => {
  it('reads the roles together with the effective administration mode', async () => {
    vi.mocked(apiRequest).mockResolvedValue({ roles: ['ADMIN'], ready: true, adminMode: false })

    await expect(getCurrentRoles()).resolves.toEqual({ roles: ['ADMIN'], ready: true, adminMode: false })

    expect(apiRequest).toHaveBeenCalledWith('/api/auth/roles')
  })

  it('switches the mode with an explicit enabled flag', async () => {
    vi.mocked(apiRequest).mockResolvedValue({ adminMode: true })

    await expect(setAdminMode(true)).resolves.toEqual({ adminMode: true })

    expect(apiRequest).toHaveBeenCalledWith('/api/auth/admin-mode', {
      method: 'PUT',
      body: JSON.stringify({ enabled: true }),
    })
  })
})

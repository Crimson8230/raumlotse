import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiRequest } from './client'
import { getRolePermissions, listRoleOptions, saveRolePermissions } from './rolePermissions'

vi.mock('./client', () => ({ apiRequest: vi.fn() }))

describe('role permission API', () => {
  beforeEach(() => vi.resetAllMocks())

  it('uses the documented list and detail endpoints', async () => {
    vi.mocked(apiRequest).mockResolvedValue({})
    await listRoleOptions()
    await getRolePermissions('UNIVERSITY_STAFF')
    expect(apiRequest).toHaveBeenNthCalledWith(1, '/api/admin/roles')
    expect(apiRequest).toHaveBeenNthCalledWith(2, '/api/admin/roles/UNIVERSITY_STAFF/permissions')
  })

  it('sends the complete selection with its opaque version', async () => {
    vi.mocked(apiRequest).mockResolvedValue({})
    await saveRolePermissions('STUDENT', ['READ', 'RESERVE'], '12')
    expect(apiRequest).toHaveBeenCalledWith('/api/admin/roles/STUDENT/permissions', {
      method: 'PUT', body: JSON.stringify({ permissions: ['READ', 'RESERVE'], expectedVersion: '12' }),
    })
  })

  it('rejects invalid selections before sending a request', async () => {
    await expect(saveRolePermissions('STUDENT', ['RESERVE'], '0')).rejects.toThrow()
    await expect(saveRolePermissions('STUDENT', ['READ', 'READ'], '0')).rejects.toThrow()
    expect(apiRequest).not.toHaveBeenCalled()
  })
})

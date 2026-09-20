import { apiRequest } from './client'
import { isValidRoleSelection } from '../types/userRole'
import type { RoleCode, UserList, UserRoles } from '../types/userRole'
export const listUsers = (q = '', page = 0) =>
  apiRequest<UserList>('/api/admin/users?' + new URLSearchParams({ q, page: String(page), size: '25' }))
export const getUserRoles = (id: string) =>
  apiRequest<UserRoles>('/api/admin/users/' + encodeURIComponent(id) + '/roles')
export function saveUserRoles(id: string, roles: RoleCode[], expectedVersion: string) {
  if (!isValidRoleSelection(roles)) return Promise.reject(new Error('Invalid role selection'))
  return apiRequest<UserRoles>('/api/admin/users/' + encodeURIComponent(id) + '/roles', {
    method: 'PUT', body: JSON.stringify({ roles, expectedVersion }),
  })
}
export const getCurrentRoles = () => apiRequest<{ roles: RoleCode[]; ready: boolean }>('/api/auth/roles')


import { apiRequest } from './client'
import { isValidRoleSelection } from '../types/userRole'
import type { RoleCode, UserList, UserRoles } from '../types/userRole'
export const listUsers = (q = '', page = 0) =>
  apiRequest<UserList>('/api/admin/users?' + new URLSearchParams({ q, page: String(page), size: '25' }))
export const getUserRoles = (id: string) =>
  apiRequest<UserRoles>('/api/admin/users/' + encodeURIComponent(id) + '/roles')
export function saveUserRoles(id: string, roles: RoleCode[], expectedVersion: string) {
  if (!isValidRoleSelection(roles)) return Promise.reject(new Error('Ungültige Rollenauswahl'))
  return apiRequest<UserRoles>('/api/admin/users/' + encodeURIComponent(id) + '/roles', {
    method: 'PUT', body: JSON.stringify({ roles, expectedVersion }),
  })
}
export interface CurrentRoles {
  roles: RoleCode[]
  ready: boolean
  /** Effective administration mode: only ever true while the user is an administrator. */
  adminMode: boolean
}
export const getCurrentRoles = () => apiRequest<CurrentRoles>('/api/auth/roles')
export const setAdminMode = (enabled: boolean) =>
  apiRequest<{ adminMode: boolean }>('/api/auth/admin-mode', { method: 'PUT', body: JSON.stringify({ enabled }) })


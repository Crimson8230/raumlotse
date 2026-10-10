import { apiRequest } from './client'
import { validPermissionSelection } from '../types/permission'
import type { PermissionCode, RoleOption, RolePermissions } from '../types/permission'
import type { RoleCode } from '../types/userRole'

export const listRoleOptions = () => apiRequest<{ items: RoleOption[] }>('/api/admin/roles')
export const getRolePermissions = (role: RoleCode) =>
  apiRequest<RolePermissions>(`/api/admin/roles/${role}/permissions`)
export function saveRolePermissions(role: RoleCode, permissions: PermissionCode[], expectedVersion: string) {
  if (!validPermissionSelection(permissions)) return Promise.reject(new Error('Ungültige Rechteauswahl'))
  return apiRequest<RolePermissions>(`/api/admin/roles/${role}/permissions`, {
    method: 'PUT', body: JSON.stringify({ permissions, expectedVersion }),
  })
}

import type { RoleCode } from './userRole'

export const permissionCodes = [
  'READ', 'RESERVE', 'OWN_RESERVATION_MANAGE', 'OTHER_RESERVATION_MANAGE',
  'OWN_ACTIVE_DEVICE_CONTROL', 'BUILDING_MANAGE', 'FLOOR_MANAGE',
  'EQUIPMENT_TYPE_MANAGE', 'ROOM_MANAGE', 'MAP_MANAGE',
  'ROOM_PLACEMENT_MANAGE', 'CONNECTION_MANAGE', 'STATISTICS_READ',
  'RESERVATION_MAINTENANCE',
] as const

export type PermissionCode = typeof permissionCodes[number]
export interface RoleOption { code: RoleCode; label: string }
export interface PermissionOption { code: PermissionCode; label: string; description: string }
export interface RolePermissions {
  role: RoleOption
  permissions: PermissionCode[]
  version: string
  availablePermissions: PermissionOption[]
  protectedRoleManagement: boolean
}

export function validPermissionSelection(value: unknown): value is PermissionCode[] {
  return Array.isArray(value) && value.length <= permissionCodes.length
    && new Set(value).size === value.length
    && value.every(code => permissionCodes.includes(code))
    && (value.length === 0 || value.includes('READ'))
}

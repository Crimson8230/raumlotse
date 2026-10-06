export const roleOptions = [
  { code: 'ADMIN', label: 'Administration' },
  { code: 'UNIVERSITY_STAFF', label: 'Hochschulpersonal' },
  { code: 'STUDENT', label: 'Studierende' },
  { code: 'LECTURER', label: 'Lehrende' },
  { code: 'VIEWER', label: 'Lesezugriff' },
] as const
export type RoleCode = typeof roleOptions[number]['code']
export interface UserSummary { id: string; displayName: string; accountLabel: string }
export interface UserRoles {
  user: UserSummary
  roles: RoleCode[]
  version: string
  availableRoles: { code: RoleCode; label: string }[]
}
export interface UserList { items: UserSummary[]; page: number; size: number; totalElements: number }
export function isValidRoleSelection(value: unknown): value is RoleCode[] {
  return Array.isArray(value) && value.length >= 1 && value.length <= 5
    && new Set(value).size === value.length
    && value.every(code => roleOptions.some(option => option.code === code))
}


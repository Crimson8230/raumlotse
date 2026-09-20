export const roleOptions = [
  { code: 'ADMIN', label: 'Admin' },
  { code: 'UNIVERSITY_STAFF', label: 'University Staff' },
  { code: 'STUDENT', label: 'Student' },
  { code: 'LECTURER', label: 'Lecturer' },
  { code: 'VIEWER', label: 'Viewer' },
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


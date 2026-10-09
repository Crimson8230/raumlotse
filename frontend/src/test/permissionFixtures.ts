export const permissionCodes = [
  'READ', 'RESERVE', 'OWN_RESERVATION_MANAGE', 'OTHER_RESERVATION_MANAGE',
  'OWN_ACTIVE_DEVICE_CONTROL', 'BUILDING_MANAGE', 'FLOOR_MANAGE',
  'EQUIPMENT_TYPE_MANAGE', 'ROOM_MANAGE', 'MAP_MANAGE',
  'ROOM_PLACEMENT_MANAGE', 'CONNECTION_MANAGE', 'STATISTICS_READ',
  'RESERVATION_MAINTENANCE',
] as const

export const viewerMembership = {
  roles: ['VIEWER'], ready: true, adminMode: false,
  permissions: ['READ'], canUseAdminMode: false,
} as const

export const adminMembership = {
  roles: ['ADMIN'], ready: true, adminMode: false,
  permissions: permissionCodes, canUseAdminMode: true,
} as const

export const multiRoleMembership = {
  roles: ['STUDENT', 'UNIVERSITY_STAFF'], ready: true, adminMode: false,
  permissions: ['READ', 'RESERVE', 'OWN_RESERVATION_MANAGE', 'BUILDING_MANAGE'],
  canUseAdminMode: true,
} as const

import { Outlet } from 'react-router-dom'
import { useCurrentRoles } from './useCurrentRoles'
import type { PermissionCode } from '../types/permission'

export function RequirePermission({ any }: { any: PermissionCode[] }) {
  const current = useCurrentRoles()
  // Keep an already granted page mounted while the right is re-checked; a revocation still removes it.
  if (current.refreshing) return <Outlet />
  if (current.loading) return <main role="status">Berechtigung wird geprüft…</main>
  if (current.failed) return <main role="alert">Die Berechtigung kann derzeit nicht geprüft werden.</main>
  if (!any.some(code => current.permissions.includes(code)))
    return <main role="alert">Diese Funktion ist für Sie nicht verfügbar.</main>
  return <Outlet />
}

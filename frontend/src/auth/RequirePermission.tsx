import { Outlet } from 'react-router-dom'
import { useCurrentRoles } from './useCurrentRoles'
import type { PermissionCode } from '../types/permission'

export function RequirePermission({ any }: { any: PermissionCode[] }) {
  const current = useCurrentRoles()
  // Keep an already granted page mounted while the right is re-checked; a revocation still removes it.
  if (current.refreshing) return <Outlet />
  if (current.loading) return <main id="inhalt"><p role="status">Berechtigung wird geprüft…</p></main>
  if (current.failed) return <main id="inhalt"><p role="alert">Die Berechtigung kann derzeit nicht geprüft werden.</p></main>
  if (!any.some(code => current.permissions.includes(code)))
    return <main id="inhalt"><p role="alert">Diese Funktion ist für Sie nicht verfügbar.</p></main>
  return <Outlet />
}

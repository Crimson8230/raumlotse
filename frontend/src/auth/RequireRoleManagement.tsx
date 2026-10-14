import { Outlet } from 'react-router-dom'
import { useCurrentRoles } from './useCurrentRoles'

export function RequireRoleManagement() {
  const roles = useCurrentRoles()
  // Keep an already granted page mounted while the right is re-checked; a revocation still removes it.
  if (roles.refreshing) return <Outlet />
  if (roles.loading) return <main id="inhalt"><p role="status">Berechtigung wird geprüft…</p></main>
  if (roles.failed) return <main id="inhalt"><p role="alert">Die Berechtigung kann derzeit nicht geprüft werden.</p></main>
  if (!roles.admin) return <main id="inhalt"><p role="alert">Diese Seite ist nur für Administratoren verfügbar.</p></main>
  return <Outlet />
}

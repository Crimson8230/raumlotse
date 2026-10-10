import { Outlet } from 'react-router-dom'
import { useCurrentRoles } from './useCurrentRoles'

export function RequireRoleManagement() {
  const roles = useCurrentRoles()
  if (roles.loading) return <main role="status">Berechtigung wird geprüft…</main>
  if (roles.failed) return <main role="alert">Die Berechtigung kann derzeit nicht geprüft werden.</main>
  if (!roles.admin) return <main role="alert">Diese Seite ist nur für Administratoren verfügbar.</main>
  return <Outlet />
}

import { Outlet } from 'react-router-dom'
import { useCurrentRoles } from './useCurrentRoles'
export function RequireAdmin() {
  const access = useCurrentRoles()
  if (access.loading) return <main role="status" className="role-page">Berechtigung wird geprüft…</main>
  if (!access.admin) return <main className="role-page" role="alert">
    {access.failed ? 'Rollenverwaltung ist derzeit nicht verfügbar.' : 'Kein Zugriff auf die Rollenverwaltung.'}
    {access.failed && <button onClick={() => window.dispatchEvent(new Event('raumlotse:roles-changed'))}>Erneut versuchen</button>}
  </main>
  return <Outlet />
}


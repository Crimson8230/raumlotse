import { useState } from 'react'
import { Outlet } from 'react-router-dom'
import { formatApiError } from '../API/client'
import { useAdminMode } from './useAdminMode'

/** Guards the administration address space; individual pages enforce their own function right. */
export function RequireAdminMode() {
  const { loading, failed, canUseAdminMode, adminMode, setMode } = useAdminMode()
  const [error, setError] = useState<string>()

  if (loading) return <main role="status" className="role-page">Berechtigung wird geprüft…</main>
  if (failed) {
    return (
      <main className="role-page" role="alert">
        Die Berechtigung kann derzeit nicht geprüft werden.
        <button type="button" onClick={() => window.dispatchEvent(new Event('raumlotse:roles-changed'))}>
          Erneut versuchen
        </button>
      </main>
    )
  }
  if (!canUseAdminMode) {
    return <main className="role-page" role="alert">Diese Seite ist für Sie nicht verfügbar.</main>
  }
  if (!adminMode) {
    return (
      <main className="role-page" role="alert">
        <p>Diese Seite gehört zum Administrationsmodus.</p>
        <button
          type="button"
          onClick={() => {
            setError(undefined)
            setMode(true).catch((err: unknown) => setError(formatApiError(err)))
          }}
        >
          Administrationsmodus aktivieren
        </button>
        {error && <p role="alert">{error}</p>}
      </main>
    )
  }
  return <Outlet />
}

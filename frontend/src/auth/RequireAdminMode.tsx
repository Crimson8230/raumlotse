import { useState } from 'react'
import { Outlet } from 'react-router-dom'
import { formatApiError } from '../API/client'
import { useAdminMode } from './useAdminMode'
import { ErrorMessage } from '../components/ui'

/** Guards the administration address space; individual pages enforce their own function right. */
export function RequireAdminMode() {
  const { loading, refreshing, failed, canUseAdminMode, adminMode, setMode } = useAdminMode()
  const [error, setError] = useState<string>()

  // Keep an already granted page mounted while the mode is re-checked; a revocation still removes it.
  if (refreshing) return <Outlet />
  if (loading) return <main id="inhalt" className="role-page"><p role="status">Berechtigung wird geprüft…</p></main>
  if (failed) {
    return (
      <main id="inhalt" className="role-page">
        <div role="alert">
          Die Berechtigung kann derzeit nicht geprüft werden.
          <button type="button" onClick={() => window.dispatchEvent(new Event('raumlotse:roles-changed'))}>
            Erneut versuchen
          </button>
        </div>
      </main>
    )
  }
  if (!canUseAdminMode) {
    return <main id="inhalt" className="role-page"><p role="alert">Diese Seite ist für Sie nicht verfügbar.</p></main>
  }
  if (!adminMode) {
    return (
      <main id="inhalt" className="role-page">
        <div role="alert">
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
          {error && <ErrorMessage>{error}</ErrorMessage>}
        </div>
      </main>
    )
  }
  return <Outlet />
}

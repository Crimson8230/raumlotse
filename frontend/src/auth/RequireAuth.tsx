import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { Navigation } from '../components/Navigation/Navigation'
import { SkipLink } from '../components/ui'
import { useAuth } from './useAuth'

export function RequireAuth() {
  const { state } = useAuth()
  const location = useLocation()
  if (state === 'loading' || state === 'finishing-login') {
    return <main id="inhalt" aria-live="polite" className="auth-status">Anmeldung wird geprüft…</main>
  }
  if (state === 'unavailable') {
    return <main id="inhalt" className="auth-status"><p role="alert">Die Anmeldung ist vorübergehend nicht verfügbar. Seite neu laden und erneut versuchen.</p></main>
  }
  if (state !== 'authenticated') return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  return <><SkipLink targetId="inhalt" /><Navigation /><Outlet /></>
}

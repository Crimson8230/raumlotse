import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { Navigation } from '../components/Navigation/Navigation'
import { useAuth } from './useAuth'

export function RequireAuth() {
  const { state } = useAuth()
  const location = useLocation()
  if (state === 'loading' || state === 'finishing-login') {
    return <main aria-live="polite" className="auth-status">Anmeldung wird geprüft…</main>
  }
  if (state === 'unavailable') {
    return <main role="alert" className="auth-status">Die Anmeldung ist vorübergehend nicht verfügbar. Seite neu laden und erneut versuchen.</main>
  }
  if (state !== 'authenticated') return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  return <><Navigation /><Outlet /></>
}

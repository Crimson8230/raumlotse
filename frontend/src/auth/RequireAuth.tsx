import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { Navigation } from '../components/Navigation/Navigation'
import { useAuth } from './useAuth'

export function RequireAuth() {
  const { state } = useAuth()
  const location = useLocation()
  if (state === 'loading' || state === 'finishing-login') {
    return <main aria-live="polite" className="auth-status">Checking your sign-in…</main>
  }
  if (state === 'unavailable') {
    return <main role="alert" className="auth-status">Sign-in is temporarily unavailable. Refresh to try again.</main>
  }
  if (state !== 'authenticated') return <Navigate to="/login" replace state={{ from: location.pathname }} />
  return <><Navigation /><Outlet /></>
}

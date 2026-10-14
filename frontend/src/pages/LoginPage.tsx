import { useEffect, useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { canonicalEmail, loginSchema } from '../auth/loginSchema'
import { ApiError } from '../API/client'
import { useAuth } from '../auth/useAuth'
import { safeReturnPath } from '../auth/returnPath'
import { Footer } from '../components/Footer/Footer'
import { ErrorMessage, Field, SkipLink } from '../components/ui'
import './LoginPage.css'

export default function LoginPage() {
  const { state, user, login, retryAvailability } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [cooldowns, setCooldowns] = useState<Record<string, number>>({})
  const [now, setNow] = useState(0)

  useEffect(() => {
    const tick = () => setNow(Date.now())
    const timer = window.setInterval(tick, 1000)
    document.addEventListener('visibilitychange', tick)
    window.addEventListener('focus', tick)
    return () => {
      window.clearInterval(timer)
      document.removeEventListener('visibilitychange', tick)
      window.removeEventListener('focus', tick)
    }
  }, [])

  const returnPath = safeReturnPath((location.state as { from?: unknown } | null)?.from)

  if (state === 'authenticated') return <Navigate to={returnPath} replace />

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    const parsed = loginSchema.safeParse({ email, password })
    if (!parsed.success) {
      setError(parsed.error.issues[0]?.message ?? 'Bitte E-Mail-Adresse und Passwort prüfen.')
      return
    }
    const emailKey = parsed.data.email.toLowerCase()
    if ((cooldowns[emailKey] ?? 0) > Date.now()) {
      setError('Zu viele fehlgeschlagene Anmeldeversuche. Bitte warten Sie, bevor Sie es erneut versuchen.')
      return
    }
    setBusy(true)
    try {
      await login({ email, password })
      setPassword('')
      navigate(returnPath, { replace: true })
    } catch (failure) {
      setPassword('')
      if (failure instanceof ApiError && (failure.status === 429 || (failure.status === 401 && failure.retryAfterSeconds))) {
        const retry = Math.max(1, failure.retryAfterSeconds ?? 1)
        const receivedAt = Date.now()
        setNow(receivedAt)
        setCooldowns((current) => ({ ...current, [emailKey]: receivedAt + retry * 1000 }))
        setPassword('')
        setError('Zu viele fehlgeschlagene Anmeldeversuche. Bitte warten Sie, bevor Sie es erneut versuchen.')
      } else {
        setError(failure instanceof ApiError && failure.status === 401
          ? 'E-Mail-Adresse oder Passwort ist falsch.'
          : 'Die Anmeldung ist vorübergehend nicht verfügbar. Bitte versuchen Sie es erneut.')
      }
    } finally {
      setBusy(false)
    }
  }

  async function retrySessionSetup() {
    setBusy(true)
    setError('')
    try {
      await retryAvailability()
      navigate(returnPath, { replace: true })
    } catch {
      setError('Die Anmeldung ist vorübergehend nicht verfügbar. Bitte versuchen Sie es erneut.')
    } finally {
      setBusy(false)
    }
  }

  const currentEmailKey = canonicalEmail(email)
  const remaining = Math.max(0, Math.ceil(((cooldowns[currentEmailKey] ?? 0) - now) / 1000))

  return (
    <>
    <SkipLink targetId="inhalt" />
    <main id="inhalt" className="login-page">
      <form className="login-card" onSubmit={submit} aria-labelledby="login-title" noValidate>
        <h1 id="login-title">Anmelden</h1>
        <p>Melden Sie sich mit E-Mail-Adresse und Passwort bei Raumlotse an.</p>
        <p className="form-required-hint">Pflichtfelder sind mit „Pflichtfeld“ gekennzeichnet.</p>
        <Field label="E-Mail-Adresse" required>
          {(control) => (
            <input {...control} name="email" type="email" autoComplete="username" required maxLength={254}
              value={email} onChange={(event) => setEmail(event.target.value)} disabled={busy} />
          )}
        </Field>
        <Field label="Passwort" required>
          {(control) => (
            <input {...control} name="password" type="password" autoComplete="current-password" required
              maxLength={1024} value={password} onChange={(event) => setPassword(event.target.value)} disabled={busy} />
          )}
        </Field>
        {remaining > 0
          ? <p className="login-error" role="status">Too many failed login attempts. Try again in {Math.floor(remaining / 60)}:{String(remaining % 60).padStart(2, '0')}.</p>
          : error && <ErrorMessage>{error}</ErrorMessage>}
        <button type="submit" className="btn-primary" disabled={busy || remaining > 0 || state === 'loading' || state === 'finishing-login' || (state === 'unavailable' && !!user)}>
          {busy ? 'Anmeldung läuft…' : 'Anmelden'}
        </button>
        {state === 'unavailable' && user && <button type="button" onClick={retrySessionSetup} disabled={busy}>
          {busy ? 'Neuer Versuch…' : 'Verbindung erneut versuchen'}
        </button>}
      </form>
    </main>
    <Footer />
    </>
  )
}

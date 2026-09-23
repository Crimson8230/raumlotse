import { useEffect, useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { canonicalEmail, loginSchema } from '../auth/loginSchema'
import { ApiError } from '../API/client'
import { useAuth } from '../auth/useAuth'
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

  if (state === 'authenticated') return <Navigate to="/" replace />

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    const parsed = loginSchema.safeParse({ email, password })
    if (!parsed.success) {
      setError(parsed.error.issues[0]?.message ?? 'Check your email address and password.')
      return
    }
    const emailKey = parsed.data.email.toLowerCase()
    if ((cooldowns[emailKey] ?? 0) > Date.now()) {
      setError('Too many failed login attempts. Please wait before trying again.')
      return
    }
    setBusy(true)
    try {
      await login({ email, password })
      setPassword('')
      navigate('/', { replace: true, state: { from: location.state?.from } })
    } catch (failure) {
      setPassword('')
      if (failure instanceof ApiError && (failure.status === 429 || (failure.status === 401 && failure.retryAfterSeconds))) {
        const retry = Math.max(1, failure.retryAfterSeconds ?? 1)
        const receivedAt = Date.now()
        setNow(receivedAt)
        setCooldowns((current) => ({ ...current, [emailKey]: receivedAt + retry * 1000 }))
        setPassword('')
        setError('Too many failed login attempts. Please wait before trying again.')
      } else {
        setError(failure instanceof ApiError && failure.status === 401
          ? 'Email address or password is incorrect.'
          : 'Sign-in is temporarily unavailable. Please try again.')
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
      navigate('/', { replace: true })
    } catch {
      setError('Sign-in is temporarily unavailable. Please try again.')
    } finally {
      setBusy(false)
    }
  }

  const currentEmailKey = canonicalEmail(email)
  const remaining = Math.max(0, Math.ceil(((cooldowns[currentEmailKey] ?? 0) - now) / 1000))

  return (
    <main className="login-page">
      <form className="login-card" onSubmit={submit} aria-labelledby="login-title" noValidate>
        <h1 id="login-title">Sign in</h1>
        <p>Use your email address and password to access Raumlotse.</p>
        <label htmlFor="login-email">Email address</label>
        <input id="login-email" name="email" type="email" autoComplete="username" required maxLength={254}
          value={email} onChange={(event) => setEmail(event.target.value)} disabled={busy} />
        <label htmlFor="login-password">Password</label>
        <input id="login-password" name="password" type="password" autoComplete="current-password" required
          maxLength={1024} value={password} onChange={(event) => setPassword(event.target.value)} disabled={busy} />
        {remaining > 0
          ? <p className="login-error" role="status">Too many failed login attempts. Try again in {Math.floor(remaining / 60)}:{String(remaining % 60).padStart(2, '0')}.</p>
          : error && <p className="login-error" role="alert">{error}</p>}
        <button type="submit" disabled={busy || remaining > 0 || state === 'loading' || state === 'finishing-login' || (state === 'unavailable' && !!user)}>
          {busy ? 'Signing in…' : 'Sign in'}
        </button>
        {state === 'unavailable' && user && <button type="button" onClick={retrySessionSetup} disabled={busy}>
          {busy ? 'Retrying…' : 'Retry connection'}
        </button>}
      </form>
    </main>
  )
}

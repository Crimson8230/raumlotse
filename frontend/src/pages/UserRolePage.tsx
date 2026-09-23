import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ApiError } from '../API/client'
import { getUserRoles, saveUserRoles } from '../API/userRoles'
import { UserRoleEditor } from '../components/UserRoles/UserRoleEditor'
import { useAuth } from '../auth/useAuth'
import { isValidRoleSelection } from '../types/userRole'
import type { RoleCode, UserRoles } from '../types/userRole'

type View = { id: string; data?: UserRoles; draft: RoleCode[]; error?: string; review?: boolean; denied?: boolean; missing?: boolean; success?: boolean }
function failed(id: string, previous: View | null, error: unknown): View {
  const state = previous?.id === id ? previous : { id, draft: [] }
  if (error instanceof ApiError) {
    if (error.status === 401 || error.status === 403) return { id, draft: [], denied: true }
    if (error.status === 404) return { id, draft: [], missing: true }
    if (error.problem?.code === 'LAST_ADMIN_REQUIRED') return { ...state, success: false, error: 'Mindestens ein Admin muss erhalten bleiben.' }
    if (error.problem?.code === 'STALE_ROLES') return { ...state, success: false, review: true, error: 'Die Rollen wurden geändert. Bitte die aktuellen Rollen prüfen.' }
    if (error.status === 400) return { ...state, success: false, error: 'Die Auswahl ist ungültig. Bitte die Rollen prüfen.' }
  }
  return { ...state, success: false, review: true, error: 'Der Vorgang konnte nicht bestätigt werden. Bitte die aktuellen Rollen prüfen.' }
}

export default function UserRolePage() {
  const { userId = '' } = useParams()
  const { user } = useAuth()
  const navigate = useNavigate()
  const [view, setView] = useState<View | null>(null)
  const [busy, setBusy] = useState(false)
  const [attempt, setAttempt] = useState(0)
  const generation = useRef({ active: false })
  const alert = useRef<HTMLParagraphElement>(null)
  useEffect(() => {
    let active = true
    const request = { active: true }
    generation.current = request
    void getUserRoles(userId).then(data => {
      if (active) setView({ id: userId, data, draft: [...data.roles] })
    }).catch(error => {
      if (active) setView(previous => failed(userId, previous, error))
    }).finally(() => { if (active) setBusy(false) })
    return () => { active = false; request.active = false }
  }, [userId, attempt])
  useEffect(() => {
    if (view?.review) alert.current?.focus()
    if (view?.denied) window.dispatchEvent(new Event('raumlotse:roles-changed'))
  }, [view?.review, view?.denied])
  const current = view?.id === userId ? view : null
  const save = async () => {
    if (!current?.data || current.review || busy || !isValidRoleSelection(current.draft)) return
    const version = generation.current
    setBusy(true)
    try {
      const data = await saveUserRoles(userId, current.draft, current.data.version)
      if (!version.active) return
      if (user?.userId === userId && !data.roles.includes('ADMIN')) {
        window.dispatchEvent(new Event('raumlotse:roles-changed'))
        navigate('/', { replace: true })
        return
      }
      setView({ id: userId, data, draft: [...data.roles], success: true })
      window.dispatchEvent(new Event('raumlotse:roles-changed'))
    } catch (error) {
      if (version.active) setView(previous => failed(userId, previous, error))
    } finally { if (version.active) setBusy(false) }
  }
  return <main className="role-page">
    <h1>Benutzerrollen bearbeiten</h1>
    {!current && <p role="status">Rollen werden geladen…</p>}
    {current?.denied && <p role="alert">Kein Zugriff auf die Rollenverwaltung.</p>}
    {current?.missing && <div role="alert">Der Benutzer existiert nicht mehr. <Link to="/admin/users">Zur Benutzerliste</Link></div>}
    {current?.error && <p role="alert" tabIndex={-1} ref={alert}>{current.error}</p>}
    {current?.review && <button disabled={busy} onClick={() => {
      setBusy(true)
      setAttempt(value => value + 1)
    }}>Aktuelle Rollen prüfen</button>}
    {current?.data && !current.denied && !current.missing && <>
      <h2>{current.data.user.displayName}</h2>
      <p className="role-account-label">{current.data.user.accountLabel}</p>
      {current.success && <p role="status">Rollen gespeichert.</p>}
      <UserRoleEditor value={current.draft} busy={busy} disabled={Boolean(current.review)}
        onChange={draft => setView({ ...current, draft, success: false, error: undefined })}
        onSave={() => void save()} onCancel={() => navigate('/admin/users')} />
    </>}
    {current && !current.data && !current.denied && !current.missing && !current.error && <p role="status">Rollen werden geladen…</p>}
  </main>
}

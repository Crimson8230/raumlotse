import { useEffect, useState } from 'react'
import { ApiError, formatApiError } from '../API/client'
import { getRolePermissions, listRoleOptions, saveRolePermissions } from '../API/rolePermissions'
import type { PermissionCode, RoleOption, RolePermissions } from '../types/permission'
import type { RoleCode } from '../types/userRole'
import './RolePermissionPage.css'

export default function RolePermissionPage() {
  const [roles, setRoles] = useState<RoleOption[]>([])
  const [selected, setSelected] = useState<RoleCode>('ADMIN')
  const [saved, setSaved] = useState<RolePermissions>()
  const [draft, setDraft] = useState<PermissionCode[]>([])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string>()
  const [review, setReview] = useState(false)
  const [success, setSuccess] = useState(false)

  useEffect(() => {
    let active = true
    listRoleOptions().then(data => { if (active) setRoles(data.items) })
      .catch(err => { if (active) setError(formatApiError(err)) })
    return () => { active = false }
  }, [])

  useEffect(() => {
    let active = true
    getRolePermissions(selected).then(data => {
      if (active) { setSaved(data); setDraft([...data.permissions]) }
    }).catch(err => { if (active) { setSaved(undefined); setDraft([]); setError(formatApiError(err)) } })
    return () => { active = false }
  }, [selected])

  const reload = async () => {
    setBusy(true)
    try {
      const data = await getRolePermissions(selected)
      setSaved(data)
      setDraft([...data.permissions])
      setReview(false)
      setError(undefined)
    } catch (err) { setSaved(undefined); setDraft([]); setError(formatApiError(err)) }
    finally { setBusy(false) }
  }

  const save = async () => {
    if (!saved || busy || review || invalidRead) return
    setBusy(true)
    setError(undefined)
    try {
      const data = await saveRolePermissions(selected, draft, saved.version)
      setSaved(data)
      setDraft([...data.permissions])
      setSuccess(true)
      window.dispatchEvent(new Event('raumlotse:roles-changed'))
    } catch (err) {
      if (err instanceof ApiError && err.problem?.code === 'STALE_ROLE_PERMISSIONS') {
        setReview(true)
        setError('Die Rechte wurden zwischenzeitlich geändert. Bitte den aktuellen Stand laden und erneut auswählen.')
      } else setError(formatApiError(err))
    } finally { setBusy(false) }
  }

  const invalidRead = draft.length > 0 && !draft.includes('READ')
  const toggle = (code: PermissionCode) => {
    setDraft(current => current.includes(code) ? current.filter(item => item !== code) : [...current, code])
    setSuccess(false)
  }

  return <main className="role-permission-page">
    <h1>Rollenrechte verwalten</h1>
    <p>Die fünf Rollen sind fest vorgegeben. Rechte mehrerer Rollen werden für einen Nutzer vereinigt.</p>
    <label htmlFor="permission-role">Rolle</label>
    <select id="permission-role" value={selected} onChange={event => {
      setSaved(undefined)
      setDraft([])
      setError(undefined)
      setReview(false)
      setSuccess(false)
      setSelected(event.target.value as RoleCode)
    }}>
      {roles.map(role => <option key={role.code} value={role.code}>{role.label}</option>)}
    </select>
    {error && <p role="alert">{error}</p>}
    {error && !saved && <button type="button" onClick={() => void reload()} disabled={busy}>Erneut laden</button>}
    {review && <button type="button" onClick={() => void reload()} disabled={busy}>Aktuellen Stand laden</button>}
    {success && <p role="status">Rollenrechte gespeichert.</p>}
    {!saved && !error && <p role="status">Rollenrechte werden geladen…</p>}
    {saved && <>
      {(saved.protectedRoleManagement || selected !== 'ADMIN') && <p className="protected-role-right">
        Rollen und Rollenrechte verwalten: fest mit der Admin-Rolle verbunden; für diese Rolle nicht verfügbar und nicht änderbar.
      </p>}
      <fieldset disabled={busy || review}>
        <legend>Funktionen für {saved.role.label}</legend>
        {saved.availablePermissions.map(option => <label className="permission-option" key={option.code}>
          <input type="checkbox" checked={draft.includes(option.code)} onChange={() => toggle(option.code)} />
          <span><strong>{option.label}</strong><small>{option.description}</small></span>
        </label>)}
      </fieldset>
      {invalidRead && <p role="alert">Für weitere Rechte muss „Lesen“ in derselben Rolle aktiviert sein.</p>}
      <div className="permission-actions">
        <button type="button" onClick={() => void save()} disabled={busy || review || invalidRead}>Speichern</button>
        <button type="button" onClick={() => { setDraft([...saved.permissions]); setError(undefined); setSuccess(false) }}
          disabled={busy || review}>Abbrechen</button>
      </div>
    </>}
  </main>
}

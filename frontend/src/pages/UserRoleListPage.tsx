import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { ApiError } from '../API/client'
import { listUsers } from '../API/userRoles'
import type { UserList } from '../types/userRole'
import '../components/UserRoles/UserRoleEditor.css'

export default function UserRoleListPage() {
  const [draft, setDraft] = useState('')
  const [query, setQuery] = useState({ q: '', page: 0, attempt: 0 })
  const [result, setResult] = useState<{ query: typeof query; data?: UserList; denied?: boolean; failed?: boolean } | null>(null)
  const sequence = useRef(0)
  useEffect(() => {
    const current = ++sequence.current
    let active = true
    void listUsers(query.q, query.page).then(data => {
      if (active && current === sequence.current) setResult({ query, data })
    }).catch(error => {
      if (active && current === sequence.current) {
        const denied = error instanceof ApiError && (error.status === 401 || error.status === 403)
        setResult({ query, denied, failed: !denied })
        if (denied) window.dispatchEvent(new Event('raumlotse:roles-changed'))
      }
    })
    return () => { active = false }
  }, [query])
  const current = result?.query === query ? result : null
  return <main className="role-page">
    <h1>Benutzerrollen</h1>
    <form className="role-search" onSubmit={event => { event.preventDefault(); setQuery({ q: draft.trim(), page: 0, attempt: 0 }) }}>
      <label htmlFor="user-role-search">Name oder Kontokennung</label>
      <input id="user-role-search" type="search" maxLength={100} value={draft} onChange={e => setDraft(e.target.value)} />
      <button type="submit">Suchen</button>
    </form>
    {!current && <p role="status">Benutzer werden geladen…</p>}
    {current?.denied && <p role="alert">Kein Zugriff auf die Rollenverwaltung.</p>}
    {current?.failed && <div role="alert">Benutzer konnten nicht geladen werden.
      <button onClick={() => setQuery({ ...query, attempt: query.attempt + 1 })}>Erneut versuchen</button>
    </div>}
    {current?.data && <>
      {current.data.items.length === 0 ? <p role="status">Keine Benutzer gefunden.</p> :
        <ul className="role-users">{current.data.items.map(user => <li key={user.id}>
          <Link to={'/admin/users/' + encodeURIComponent(user.id) + '/roles'}><strong>{user.displayName}</strong><span>{user.accountLabel}</span></Link>
        </li>)}</ul>}
      <div className="role-pagination">
        <button disabled={query.page === 0} onClick={() => setQuery({ ...query, page: query.page - 1 })}>Zurück</button>
        <span aria-live="polite">Seite {query.page + 1} · {current.data.totalElements} Benutzer</span>
        <button disabled={(query.page + 1) * current.data.size >= current.data.totalElements}
          onClick={() => setQuery({ ...query, page: query.page + 1 })}>Weiter</button>
      </div>
    </>}
  </main>
}


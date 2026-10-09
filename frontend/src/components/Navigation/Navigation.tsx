import { BarChart3, Building2, DoorOpen, Home, Map, MapPinned, ShieldCheck, Users } from 'lucide-react'
import { NavLink } from 'react-router-dom'
import { useContext, useState } from 'react'
import { formatApiError } from '../../API/client'
import { AuthContext } from '../../auth/authContext'
import { useAdminMode } from '../../auth/useAdminMode'
import type { PermissionCode } from '../../types/permission'
import './Navigation.css'

interface NavItem {
  label: string
  path: string
  icon: typeof Home
}

const userItems: NavItem[] = [
  { label: 'Home', path: '/', icon: Home },
  { label: 'Räume', path: '/rooms', icon: DoorOpen },
  { label: 'Karten', path: '/maps', icon: Map },
]

const adminItems: (NavItem & { rights?: PermissionCode[]; adminOnly?: boolean })[] = [
  { label: 'Standorte', path: '/admin/locations', icon: Building2,
    rights: ['BUILDING_MANAGE', 'FLOOR_MANAGE', 'EQUIPMENT_TYPE_MANAGE'] },
  { label: 'Karten bearbeiten', path: '/admin/maps', icon: MapPinned,
    rights: ['MAP_MANAGE', 'ROOM_PLACEMENT_MANAGE', 'CONNECTION_MANAGE'] },
  { label: 'Statistiken', path: '/admin/statistics', icon: BarChart3, rights: ['STATISTICS_READ'] },
  { label: 'Benutzerrollen', path: '/admin/users', icon: Users, adminOnly: true },
  { label: 'Rollenrechte', path: '/admin/roles', icon: ShieldCheck, adminOnly: true },
]

function Item({ label, path, icon: Icon }: NavItem) {
  return (
    <li>
      <NavLink to={path} end={path === '/'} className="nav-link">
        <Icon className="nav-icon" size={18} aria-hidden="true" />
        <span className="nav-label">{label}</span>
      </NavLink>
    </li>
  )
}

export function Navigation() {
  const auth = useContext(AuthContext)
  const { admin, adminMode, canUseAdminMode, permissions, setMode } = useAdminMode(auth?.state === 'authenticated')
  const [error, setError] = useState<string>()

  function toggle(enabled: boolean) {
    setError(undefined)
    setMode(enabled).catch((err: unknown) => setError(formatApiError(err)))
  }

  return (
    <nav className={adminMode ? 'nav nav-admin' : 'nav'} aria-label="Hauptnavigation">
      <div className="nav-inner">
      <ul className="nav-list">
        {permissions.includes('READ') && userItems.map((item) => <Item key={item.path} {...item} />)}
        {adminMode && adminItems.filter(item => item.adminOnly ? admin
          : item.rights?.some(right => permissions.includes(right))).map((item) => <Item key={item.path} {...item} />)}
      </ul>
      {canUseAdminMode && (
        <div className="nav-admin-mode">
          {adminMode && (
            <span className="nav-admin-indicator" role="status">
              <ShieldCheck size={16} aria-hidden="true" /> Administrationsmodus aktiv
            </span>
          )}
          <button type="button" aria-pressed={adminMode} onClick={() => toggle(!adminMode)}>
            {adminMode ? 'Administrationsmodus beenden' : 'Administrationsmodus'}
          </button>
          {error && <span role="alert">{error}</span>}
        </div>
      )}
      </div>
    </nav>
  )
}

export default Navigation

import { Building2, DoorOpen, Home } from 'lucide-react'
import { NavLink } from 'react-router-dom'
import './Navigation.css'

interface NavItem {
  label: string
  path: string
  icon: typeof Home
}

const items: NavItem[] = [
  { label: 'Home', path: '/', icon: Home },
  { label: 'Standorte', path: '/locations', icon: Building2 },
  { label: 'Räume', path: '/rooms', icon: DoorOpen },
]

export function Navigation() {
  return (
    <nav className="nav" aria-label="Hauptnavigation">
      <ul className="nav-list">
        {items.map(({ label, path, icon: Icon }) => (
          <li key={path}>
            <NavLink to={path} end={path === '/'} className="nav-link">
              <Icon className="nav-icon" size={18} aria-hidden="true" />
              <span className="nav-label">{label}</span>
            </NavLink>
          </li>
        ))}
      </ul>
    </nav>
  )
}

export default Navigation

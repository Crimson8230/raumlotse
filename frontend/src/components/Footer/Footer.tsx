import { Link } from 'react-router-dom'
import './Footer.css'

/** Fußzeile des Anwendungsrahmens: immer an derselben Stelle mit demselben Hilfelink (WCAG 3.2.6). */
export function Footer() {
  return (
    <footer className="app-footer">
      <Link to="/hilfe" className="app-footer__link ui-target">Hilfe</Link>
    </footer>
  )
}

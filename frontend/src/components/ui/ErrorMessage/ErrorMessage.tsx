import type { ReactNode } from 'react'
import { TriangleAlert } from 'lucide-react'
import { fieldLabel } from '../fieldLabels'
import '../ui.css'
import './ErrorMessage.css'

interface ErrorMessageProps {
  id?: string
  /** Technischer Feldname vom Server; angezeigt wird die Beschriftung aus der Zuordnung. */
  field?: string
  children: ReactNode
}

/** Einheitliches Fehlermuster: Symbol + "Fehler:" + Text, wird beim Erscheinen angesagt. */
export function ErrorMessage({ id, field, children }: ErrorMessageProps) {
  return (
    <p id={id} role="alert" className="ui-error">
      <TriangleAlert className="ui-error__symbol" size={18} aria-hidden="true" />
      <span>
        <strong>Fehler:</strong> {field ? `${fieldLabel(field)}: ` : ''}
        {children}
      </span>
    </p>
  )
}

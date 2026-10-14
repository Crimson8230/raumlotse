import { useId, type ReactNode } from 'react'
import { ErrorMessage } from '../ErrorMessage/ErrorMessage'
import '../ui.css'
import './Field.css'

export interface FieldControlProps {
  id: string
  'aria-describedby'?: string
  'aria-invalid'?: true
  'aria-required'?: true
}

interface FieldProps {
  label: string
  required?: boolean
  hint?: string
  error?: string
  /** Rendert das Eingabeelement; die übergebenen Props verbinden Beschriftung, Hinweis und Fehler. */
  children: (control: FieldControlProps) => ReactNode
}

/** Formularfeld mit sichtbarer Beschriftung, Pflichtfeld-Wort, Hinweis und Fehler, alles mit dem Eingabeelement verbunden. */
export function Field({ label, required, hint, error, children }: FieldProps) {
  const id = useId()
  const hintId = hint ? `${id}-hint` : undefined
  const errorId = error ? `${id}-error` : undefined
  const describedBy = [errorId, hintId].filter(Boolean).join(' ')

  const control: FieldControlProps = { id }
  if (describedBy) control['aria-describedby'] = describedBy
  if (error) control['aria-invalid'] = true
  if (required) control['aria-required'] = true

  return (
    <div className="ui-field">
      <div className="ui-field__head">
        <label htmlFor={id} className="ui-field__label">
          {label}
        </label>
        {required && <span className="ui-field__required">Pflichtfeld</span>}
      </div>
      {children(control)}
      {hint && (
        <p id={hintId} className="ui-field__hint">
          {hint}
        </p>
      )}
      {error && <ErrorMessage id={errorId}>{error}</ErrorMessage>}
    </div>
  )
}

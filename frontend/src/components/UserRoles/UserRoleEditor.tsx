import { isValidRoleSelection, roleOptions } from '../../types/userRole'
import type { RoleCode } from '../../types/userRole'
import './UserRoleEditor.css'
interface Props {
  value: RoleCode[]
  onChange: (roles: RoleCode[]) => void
  onSave: () => void
  onCancel: () => void
  busy: boolean
  disabled: boolean
}
export function UserRoleEditor({ value, onChange, onSave, onCancel, busy, disabled }: Props) {
  const valid = isValidRoleSelection(value)
  return <form className="role-editor" onSubmit={event => { event.preventDefault(); if (valid && !busy && !disabled) onSave() }}>
    <fieldset disabled={busy || disabled} aria-describedby={!valid ? 'role-selection-error' : undefined}>
      <legend>Rollen</legend>
      {roleOptions.map(option => <label key={option.code}>
        <input type="checkbox" checked={value.includes(option.code)} onChange={event =>
          onChange(event.target.checked ? [...value, option.code] : value.filter(code => code !== option.code))} />
        {option.label}
      </label>)}
    </fieldset>
    {!valid && <p id="role-selection-error" role="alert">Mindestens eine Rolle ist erforderlich.</p>}
    <div className="role-actions">
      <button type="submit" disabled={!valid || busy || disabled}>Speichern</button>
      <button type="button" disabled={busy} onClick={onCancel}>Abbrechen</button>
    </div>
    {busy && <p role="status">Rollen werden gespeichert…</p>}
  </form>
}


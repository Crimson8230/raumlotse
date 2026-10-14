import { useCallback, useEffect, useState } from 'react'
import {
  createEquipmentType,
  deactivateEquipmentType,
  deleteEquipmentType,
  listEquipmentTypes,
  reactivateEquipmentType,
  renameEquipmentType,
} from '../../API/equipmentTypes'
import { formatApiError } from '../../API/client'
import type { EquipmentType } from '../../types/room'
import { entityStatusLabel } from '../../utils/labels'
import { ErrorMessage, Field } from '../../components/ui'

export interface EquipmentCatalogProps {
  onCatalogChanged?: () => void
}

export function EquipmentCatalog({ onCatalogChanged }: EquipmentCatalogProps = {}) {
  const [types, setTypes] = useState<EquipmentType[]>([])
  const [newName, setNewName] = useState('')
  const [newSpecialised, setNewSpecialised] = useState(false)
  const [renameDrafts, setRenameDrafts] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)

  const fetchTypes = useCallback(() => listEquipmentTypes('all'), [])

  const reload = useCallback(async () => {
    setTypes(await fetchTypes())
    onCatalogChanged?.()
  }, [fetchTypes, onCatalogChanged])

  useEffect(() => {
    let ignore = false
    fetchTypes().then(
      (loaded) => {
        if (!ignore) setTypes(loaded)
      },
      (err) => {
        if (!ignore) setError(formatApiError(err))
      },
    )
    return () => {
      ignore = true
    }
  }, [fetchTypes])

  async function guarded(action: () => Promise<unknown>) {
    setError(null)
    try {
      await action()
      await reload()
    } catch (err) {
      setError(formatApiError(err))
    }
  }

  return (
    <section aria-label="Katalog der Ausstattungstypen" className="panel">
      <h2>Ausstattung</h2>
      {error && (
        <ErrorMessage>{error}</ErrorMessage>
      )}

      <form
        className="inline-form"
        onSubmit={(e) => {
          e.preventDefault()
          guarded(async () => {
            await (newSpecialised ? createEquipmentType(newName, undefined, true) : createEquipmentType(newName))
            setNewName('')
            setNewSpecialised(false)
          })
        }}
      >
        <Field label="Name des neuen Ausstattungstyps" required>
          {(control) => <input {...control} value={newName} onChange={(e) => setNewName(e.target.value)} />}
        </Field>
        <label className="checkbox-label">
          <input type="checkbox" checked={newSpecialised} onChange={(e) => setNewSpecialised(e.target.checked)} />
          Spezialausstattung (wirkt sich auf die Sortierung aus)
        </label>
        <button type="submit" className="btn-primary">Ausstattungstyp hinzufügen</button>
      </form>

      {types.length === 0 ? (
        <p className="status-empty">Noch keine Ausstattungstypen.</p>
      ) : (
        <ul className="list-plain">
        {types.map((type) => {
          const renameId = `rename-equipment-type-${type.id}`
          return (
            <li key={type.id}>
              <span className="catalog-name">{type.name}</span>{' '}
              <span className={type.status === 'ACTIVE' ? 'status-active' : 'status-deactivated'}>
                ({entityStatusLabel(type.status)})
              </span>
              {type.specialised && <span className="catalog-tag">Spezial</span>}

              <label className="checkbox-label">
                <input
                  type="checkbox"
                  checked={type.specialised ?? false}
                  onChange={(e) => guarded(() => renameEquipmentType(type.id, type.name, undefined, e.target.checked))}
                  aria-label={`${type.name} ist Spezialausstattung`}
                />
                Spezialausstattung
              </label>

              <div className="inline-form">
                <div>
                  <label className="sr-only" htmlFor={renameId}>{`Ausstattungstyp ${type.name} umbenennen`}</label>
                  <input
                    id={renameId}
                    value={renameDrafts[type.id] ?? type.name}
                    onChange={(e) => setRenameDrafts((prev) => ({ ...prev, [type.id]: e.target.value }))}
                  />
                </div>
                <button
                  type="button"
                  onClick={() => guarded(() => renameEquipmentType(type.id, renameDrafts[type.id] ?? type.name))}
                 aria-label={`Name des Ausstattungstyps ${type.name} speichern`}>Speichern</button>
              </div>

              <div className="actions">
                {type.status === 'ACTIVE' ? (
                  <button type="button" onClick={() => guarded(() => deactivateEquipmentType(type.id))} aria-label={`Ausstattungstyp ${type.name} deaktivieren`}>Deaktivieren</button>
                ) : (
                  <button type="button" onClick={() => guarded(() => reactivateEquipmentType(type.id))} aria-label={`Ausstattungstyp ${type.name} reaktivieren`}>Reaktivieren</button>
                )}
                <button type="button" onClick={() => guarded(() => deleteEquipmentType(type.id))} aria-label={`Ausstattungstyp ${type.name} löschen`}>Löschen</button>
              </div>
            </li>
          )
        })}
        </ul>
      )}
    </section>
  )
}

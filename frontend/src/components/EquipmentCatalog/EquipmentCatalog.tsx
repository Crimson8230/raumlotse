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

export interface EquipmentCatalogProps {
  onCatalogChanged?: () => void
}

export function EquipmentCatalog({ onCatalogChanged }: EquipmentCatalogProps = {}) {
  const [types, setTypes] = useState<EquipmentType[]>([])
  const [newName, setNewName] = useState('')
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
    <section aria-label="Equipment type catalog" className="panel">
      <h2>Ausstattung</h2>
      {error && (
        <p role="alert" className="feedback-error">
          {error}
        </p>
      )}

      <form
        className="inline-form"
        onSubmit={(e) => {
          e.preventDefault()
          guarded(async () => {
            await createEquipmentType(newName)
            setNewName('')
          })
        }}
      >
        <div>
          <label htmlFor="new-equipment-type-name">New equipment type name</label>
          <input id="new-equipment-type-name" value={newName} onChange={(e) => setNewName(e.target.value)} />
        </div>
        <button type="submit">Add equipment type</button>
      </form>

      {types.length === 0 ? (
        <p className="status-empty">No equipment types yet.</p>
      ) : (
        <ul className="list-plain">
        {types.map((type) => {
          const renameId = `rename-equipment-type-${type.id}`
          return (
            <li key={type.id}>
              <span>{type.name}</span>{' '}
              <span className={type.status === 'ACTIVE' ? 'status-active' : 'status-deactivated'}>
                ({type.status})
              </span>

              <div className="inline-form">
                <div>
                  <label htmlFor={renameId}>{`Rename equipment type ${type.name}`}</label>
                  <input
                    id={renameId}
                    value={renameDrafts[type.id] ?? type.name}
                    onChange={(e) => setRenameDrafts((prev) => ({ ...prev, [type.id]: e.target.value }))}
                  />
                </div>
                <button
                  type="button"
                  onClick={() => guarded(() => renameEquipmentType(type.id, renameDrafts[type.id] ?? type.name))}
                >
                  {`Save equipment type name for ${type.name}`}
                </button>
              </div>

              <div className="actions">
                {type.status === 'ACTIVE' ? (
                  <button type="button" onClick={() => guarded(() => deactivateEquipmentType(type.id))}>
                    {`Deactivate equipment type ${type.name}`}
                  </button>
                ) : (
                  <button type="button" onClick={() => guarded(() => reactivateEquipmentType(type.id))}>
                    {`Reactivate equipment type ${type.name}`}
                  </button>
                )}
                <button type="button" onClick={() => guarded(() => deleteEquipmentType(type.id))}>
                  {`Delete equipment type ${type.name}`}
                </button>
              </div>
            </li>
          )
        })}
        </ul>
      )}
    </section>
  )
}

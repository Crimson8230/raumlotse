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
    <section aria-label="Equipment type catalog">
      {error && <p role="alert">{error}</p>}

      <form
        onSubmit={(e) => {
          e.preventDefault()
          guarded(async () => {
            await createEquipmentType(newName)
            setNewName('')
          })
        }}
      >
        <label htmlFor="new-equipment-type-name">New equipment type name</label>
        <input id="new-equipment-type-name" value={newName} onChange={(e) => setNewName(e.target.value)} />
        <button type="submit">Add equipment type</button>
      </form>

      <ul>
        {types.map((type) => {
          const renameId = `rename-equipment-type-${type.id}`
          return (
            <li key={type.id}>
              <span>{type.name}</span>
              <span> ({type.status})</span>

              <label htmlFor={renameId}>{`Rename equipment type ${type.name}`}</label>
              <input
                id={renameId}
                value={renameDrafts[type.id] ?? type.name}
                onChange={(e) => setRenameDrafts((prev) => ({ ...prev, [type.id]: e.target.value }))}
              />
              <button
                type="button"
                onClick={() => guarded(() => renameEquipmentType(type.id, renameDrafts[type.id] ?? type.name))}
              >
                {`Save equipment type name for ${type.name}`}
              </button>

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
            </li>
          )
        })}
      </ul>
    </section>
  )
}

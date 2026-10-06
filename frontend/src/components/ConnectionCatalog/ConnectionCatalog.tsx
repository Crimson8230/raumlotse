import { useState, type FormEvent } from 'react'
import { createConnection, deleteConnection, deleteConnectionPoint, updateConnection } from '../../API/maps'
import { formatApiError } from '../../API/client'
import { CONNECTION_TYPE_LABEL } from '../FloorMap/connectionLabels'
import type { Connection, ConnectionType } from '../../types/map'

interface Props {
  connections: Connection[]
  currentMapId: string
  admin: boolean
  /** Connection whose point the next click on the map sets. */
  activeConnectionId: string | undefined
  onSelectForPlacement: (connectionId: string | undefined) => void
  onChanged: () => void
}

export function ConnectionCatalog({
  connections,
  currentMapId,
  admin,
  activeConnectionId,
  onSelectForPlacement,
  onChanged,
}: Props) {
  const [newName, setNewName] = useState('')
  const [newType, setNewType] = useState<ConnectionType>('ELEVATOR')
  const [editing, setEditing] = useState<{ id: string; name: string; type: ConnectionType }>()
  const [error, setError] = useState<string>()

  async function run(action: () => Promise<unknown>) {
    setError(undefined)
    try {
      await action()
      onChanged()
    } catch (err) {
      setError(formatApiError(err))
    }
  }

  async function create(event: FormEvent) {
    event.preventDefault()
    await run(async () => {
      await createConnection({ name: newName, type: newType })
      setNewName('')
    })
  }

  async function save(event: FormEvent) {
    event.preventDefault()
    if (!editing) return
    await run(async () => {
      await updateConnection(editing.id, { name: editing.name, type: editing.type })
      setEditing(undefined)
    })
  }

  async function remove(connection: Connection) {
    if (!window.confirm(`Verbindung „${connection.name}“ samt aller Punkte löschen?`)) return
    if (activeConnectionId === connection.id) onSelectForPlacement(undefined)
    await run(() => deleteConnection(connection.id))
  }

  return (
    <section aria-label="Verbindungen" className="connection-catalog">
      <h2>Verbindungen (Treppen und Aufzüge)</h2>
      {error && <p role="alert">{error}</p>}
      {connections.length === 0 && <p className="map-empty">Noch keine Verbindung angelegt.</p>}
      <ul>
        {connections.map((connection) => {
          const hasPointHere = connection.points.some((point) => point.mapId === currentMapId)
          const isEditing = editing?.id === connection.id
          return (
            <li key={connection.id}>
              {isEditing ? (
                <form onSubmit={save}>
                  <label>
                    Name
                    <input
                      value={editing.name}
                      maxLength={100}
                      onChange={(event) => setEditing({ ...editing, name: event.target.value })}
                    />
                  </label>
                  <label>
                    Typ
                    <select
                      value={editing.type}
                      onChange={(event) => setEditing({ ...editing, type: event.target.value as ConnectionType })}
                    >
                      <option value="ELEVATOR">Aufzug</option>
                      <option value="STAIRS">Treppe</option>
                    </select>
                  </label>
                  <button type="submit">Speichern</button>
                  <button type="button" onClick={() => setEditing(undefined)}>
                    Abbrechen
                  </button>
                </form>
              ) : (
                <>
                  <span>{connection.name}</span> <span>({CONNECTION_TYPE_LABEL[connection.type]})</span>
                  {connection.incomplete && <strong> unvollständig</strong>}
                  {hasPointHere && <span> · Punkt auf dieser Karte</span>}
                  {admin && (
                    <>
                      <button
                        type="button"
                        aria-pressed={activeConnectionId === connection.id}
                        aria-label={`Punkt setzen: ${connection.name}`}
                        onClick={() =>
                          onSelectForPlacement(activeConnectionId === connection.id ? undefined : connection.id)
                        }
                      >
                        {hasPointHere ? 'Punkt verschieben' : 'Punkt setzen'}
                      </button>
                      {hasPointHere && (
                        <button
                          type="button"
                          aria-label={`Punkt entfernen: ${connection.name}`}
                          onClick={() => void run(() => deleteConnectionPoint(connection.id, currentMapId))}
                        >
                          Punkt entfernen
                        </button>
                      )}
                      <button
                        type="button"
                        aria-label={`Bearbeiten: ${connection.name}`}
                        onClick={() => setEditing({ id: connection.id, name: connection.name, type: connection.type })}
                      >
                        Bearbeiten
                      </button>
                      <button type="button" aria-label={`Löschen: ${connection.name}`} onClick={() => void remove(connection)}>
                        Löschen
                      </button>
                    </>
                  )}
                </>
              )}
            </li>
          )
        })}
      </ul>
      {admin && (
        <form onSubmit={create} className="connection-create">
          <label>
            Name der Verbindung
            <input value={newName} maxLength={100} onChange={(event) => setNewName(event.target.value)} />
          </label>
          <label>
            Typ
            <select value={newType} onChange={(event) => setNewType(event.target.value as ConnectionType)}>
              <option value="ELEVATOR">Aufzug</option>
              <option value="STAIRS">Treppe</option>
            </select>
          </label>
          <button type="submit" disabled={newName.trim() === ''}>
            Verbindung anlegen
          </button>
        </form>
      )}
    </section>
  )
}

import { useCallback, useEffect, useState } from 'react'
import {
  createBuilding,
  deactivateBuilding,
  deleteBuilding,
  listBuildings,
  reactivateBuilding,
  renameBuilding,
  updateBuilding,
} from '../../API/buildings'
import {
  createFloor,
  deactivateFloor,
  deleteFloor,
  listFloors,
  reactivateFloor,
  renameFloor,
  updateFloor,
} from '../../API/floors'
import { formatApiError } from '../../API/client'
import type { Building, Floor } from '../../types/room'

export interface BuildingCatalogProps {
  /** Called whenever a building/floor is created, so an embedding form can refresh its pickers. */
  onCatalogChanged?: () => void
}

export function BuildingCatalog({ onCatalogChanged }: BuildingCatalogProps = {}) {
  const [buildings, setBuildings] = useState<Building[]>([])
  const [floorsByBuilding, setFloorsByBuilding] = useState<Record<string, Floor[]>>({})
  const [newBuildingName, setNewBuildingName] = useState('')
  const [newBuildingHasElevator, setNewBuildingHasElevator] = useState(false)
  const [renameDrafts, setRenameDrafts] = useState<Record<string, string>>({})
  const [newFloorNames, setNewFloorNames] = useState<Record<string, string>>({})
  const [newFloorGround, setNewFloorGround] = useState<Record<string, boolean>>({})
  const [floorRenameDrafts, setFloorRenameDrafts] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)

  const fetchCatalog = useCallback(async () => {
    const loadedBuildings = await listBuildings('all')
    const floorEntries = await Promise.all(
      loadedBuildings.map(async (b) => [b.id, await listFloors(b.id, 'all')] as const),
    )
    return { loadedBuildings, floorsByBuilding: Object.fromEntries(floorEntries) }
  }, [])

  const reload = useCallback(async () => {
    const { loadedBuildings, floorsByBuilding: loadedFloors } = await fetchCatalog()
    setBuildings(loadedBuildings)
    setFloorsByBuilding(loadedFloors)
    onCatalogChanged?.()
  }, [fetchCatalog, onCatalogChanged])

  useEffect(() => {
    let ignore = false
    fetchCatalog().then(
      (result) => {
        if (!ignore) {
          setBuildings(result.loadedBuildings)
          setFloorsByBuilding(result.floorsByBuilding)
        }
      },
      (err) => {
        if (!ignore) setError(formatApiError(err))
      },
    )
    return () => {
      ignore = true
    }
  }, [fetchCatalog])

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
    <section aria-label="Building and floor catalog" className="panel">
      <h2>Gebäude</h2>
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
            await createBuilding(newBuildingName, newBuildingHasElevator)
            setNewBuildingName('')
            setNewBuildingHasElevator(false)
          })
        }}
      >
        <div>
          <label htmlFor="new-building-name">New building name</label>
          <input
            id="new-building-name"
            value={newBuildingName}
            onChange={(e) => setNewBuildingName(e.target.value)}
          />
        </div>
        <label className="checkbox-label">
          <input
            type="checkbox"
            checked={newBuildingHasElevator}
            onChange={(e) => setNewBuildingHasElevator(e.target.checked)}
          />
          Aufzug vorhanden
        </label>
        <button type="submit">Add building</button>
      </form>

      {buildings.length === 0 ? (
        <p className="status-empty">No buildings yet.</p>
      ) : (
        <ul className="list-plain">
        {buildings.map((building) => {
          const floors = floorsByBuilding[building.id] ?? []
          const renameId = `rename-building-${building.id}`
          const newFloorId = `new-floor-${building.id}`
          return (
            <li key={building.id} className="panel">
              <span>{building.name}</span>{' '}
              <span className={building.status === 'ACTIVE' ? 'status-active' : 'status-deactivated'}>
                ({building.status})
              </span>{' '}
              <span>{building.hasElevator ? 'Aufzug' : 'kein Aufzug'}</span>

              <label className="checkbox-label">
                <input
                  type="checkbox"
                  checked={building.hasElevator}
                  onChange={() => guarded(() => updateBuilding(building.id, building.name, !building.hasElevator))}
                />
                {`Aufzug in ${building.name}`}
              </label>

              <div className="inline-form">
                <div>
                  <label htmlFor={renameId}>{`Rename building ${building.name}`}</label>
                  <input
                    id={renameId}
                    value={renameDrafts[building.id] ?? building.name}
                    onChange={(e) =>
                      setRenameDrafts((prev) => ({ ...prev, [building.id]: e.target.value }))
                    }
                  />
                </div>
                <button
                  type="button"
                  onClick={() =>
                    guarded(() => renameBuilding(building.id, renameDrafts[building.id] ?? building.name))
                  }
                >
                  {`Save building name for ${building.name}`}
                </button>
              </div>

              <div className="actions">
                {building.status === 'ACTIVE' ? (
                  <button type="button" onClick={() => guarded(() => deactivateBuilding(building.id))}>
                    {`Deactivate building ${building.name}`}
                  </button>
                ) : (
                  <button type="button" onClick={() => guarded(() => reactivateBuilding(building.id))}>
                    {`Reactivate building ${building.name}`}
                  </button>
                )}
                <button type="button" onClick={() => guarded(() => deleteBuilding(building.id))}>
                  {`Delete building ${building.name}`}
                </button>
              </div>

              <form
                className="inline-form"
                onSubmit={(e) => {
                  e.preventDefault()
                  guarded(async () => {
                    await createFloor(building.id, newFloorNames[building.id] ?? '', newFloorGround[building.id] ?? false)
                    setNewFloorNames((prev) => ({ ...prev, [building.id]: '' }))
                    setNewFloorGround((prev) => ({ ...prev, [building.id]: false }))
                  })
                }}
              >
                <div>
                  <label htmlFor={newFloorId}>{`New floor name for ${building.name}`}</label>
                  <input
                    id={newFloorId}
                    value={newFloorNames[building.id] ?? ''}
                    onChange={(e) =>
                      setNewFloorNames((prev) => ({ ...prev, [building.id]: e.target.value }))
                    }
                  />
                </div>
                <label className="checkbox-label">
                  <input
                    type="checkbox"
                    checked={newFloorGround[building.id] ?? false}
                    onChange={(e) => setNewFloorGround((prev) => ({ ...prev, [building.id]: e.target.checked }))}
                  />
                  Erdgeschoss (stufenloser Zugang)
                </label>
                <button type="submit">{`Add floor to ${building.name}`}</button>
              </form>

              {floors.length === 0 ? (
                <p className="status-empty">No floors yet.</p>
              ) : (
                <ul className="list-plain">
                {floors.map((floor) => {
                  const floorRenameId = `rename-floor-${floor.id}`
                  return (
                    <li key={floor.id}>
                      <span>{floor.name}</span>{' '}
                      <span className={floor.status === 'ACTIVE' ? 'status-active' : 'status-deactivated'}>
                        ({floor.status})
                      </span>
                      {floor.groundFloor && (
                        <>
                          {' '}
                          <span>Erdgeschoss</span>
                        </>
                      )}

                      <label className="checkbox-label">
                        <input
                          type="checkbox"
                          checked={floor.groundFloor}
                          onChange={() => guarded(() => updateFloor(floor.id, floor.name, !floor.groundFloor))}
                        />
                        {`Erdgeschoss: ${floor.name}`}
                      </label>

                      <div className="inline-form">
                        <div>
                          <label htmlFor={floorRenameId}>{`Rename floor ${floor.name}`}</label>
                          <input
                            id={floorRenameId}
                            value={floorRenameDrafts[floor.id] ?? floor.name}
                            onChange={(e) =>
                              setFloorRenameDrafts((prev) => ({ ...prev, [floor.id]: e.target.value }))
                            }
                          />
                        </div>
                        <button
                          type="button"
                          onClick={() =>
                            guarded(() => renameFloor(floor.id, floorRenameDrafts[floor.id] ?? floor.name))
                          }
                        >
                          {`Save floor name for ${floor.name}`}
                        </button>
                      </div>

                      <div className="actions">
                        {floor.status === 'ACTIVE' ? (
                          <button type="button" onClick={() => guarded(() => deactivateFloor(floor.id))}>
                            {`Deactivate floor ${floor.name}`}
                          </button>
                        ) : (
                          <button type="button" onClick={() => guarded(() => reactivateFloor(floor.id))}>
                            {`Reactivate floor ${floor.name}`}
                          </button>
                        )}
                        <button type="button" onClick={() => guarded(() => deleteFloor(floor.id))}>
                          {`Delete floor ${floor.name}`}
                        </button>
                      </div>
                    </li>
                  )
                })}
                </ul>
              )}
            </li>
          )
        })}
        </ul>
      )}
    </section>
  )
}

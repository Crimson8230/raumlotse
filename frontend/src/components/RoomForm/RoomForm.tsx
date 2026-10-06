import { useCallback, useEffect, useState } from 'react'
import { createBuilding, listBuildings } from '../../API/buildings'
import { createFloor, listFloors } from '../../API/floors'
import { listEquipmentTypes } from '../../API/equipmentTypes'
import { createRoom, updateRoom } from '../../API/rooms'
import { ApiError, formatApiError } from '../../API/client'
import type { Building, EquipmentType, Floor, Room, SeatingArrangement } from '../../types/room'
import './RoomForm.css'

export interface RoomFormProps {
  /** Provide an existing room to edit it; omit to create a new one. */
  room?: Room
  onSaved: (room: Room) => void
}

export function RoomForm({ room, onSaved }: RoomFormProps) {
  const [name, setName] = useState(room?.name ?? '')
  const [buildings, setBuildings] = useState<Building[]>([])
  const [floors, setFloors] = useState<Floor[]>([])
  const [equipmentTypes, setEquipmentTypes] = useState<EquipmentType[]>([])
  const [selectedBuildingId, setSelectedBuildingId] = useState(room?.building.id ?? '')
  const [selectedFloorId, setSelectedFloorId] = useState(room?.floor.id ?? '')
  const [selectedEquipmentIds, setSelectedEquipmentIds] = useState<string[]>(room?.equipmentTypeIds ?? [])
  const [notBarrierFree, setNotBarrierFree] = useState(room?.notBarrierFree ?? false)
  const [seatingArrangements, setSeatingArrangements] = useState<SeatingArrangement[]>(
    room?.seatingArrangements.length ? room.seatingArrangements : [{ name: '', maxCapacity: 0 }],
  )
  const [showNewBuildingForm, setShowNewBuildingForm] = useState(false)
  const [newBuildingName, setNewBuildingName] = useState('')
  const [showNewFloorForm, setShowNewFloorForm] = useState(false)
  const [newFloorName, setNewFloorName] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [errorKind, setErrorKind] = useState<'error' | 'conflict'>('error')
  const [submitting, setSubmitting] = useState(false)

  const loadBuildings = useCallback(() => listBuildings('active'), [])
  const loadEquipmentTypes = useCallback(() => listEquipmentTypes('active'), [])

  useEffect(() => {
    let ignore = false
    loadBuildings().then(
      (loaded) => {
        if (!ignore) setBuildings(loaded)
      },
      (err) => {
        if (!ignore) {
          setErrorKind('error')
          setError(formatApiError(err))
        }
      },
    )
    return () => {
      ignore = true
    }
  }, [loadBuildings])

  useEffect(() => {
    let ignore = false
    loadEquipmentTypes().then(
      (loaded) => {
        if (!ignore) setEquipmentTypes(loaded)
      },
      (err) => {
        if (!ignore) {
          setErrorKind('error')
          setError(formatApiError(err))
        }
      },
    )
    return () => {
      ignore = true
    }
  }, [loadEquipmentTypes])

  useEffect(() => {
    if (!selectedBuildingId) {
      return
    }
    let ignore = false
    listFloors(selectedBuildingId, 'active').then(
      (loaded) => {
        if (!ignore) setFloors(loaded)
      },
      (err) => {
        if (!ignore) {
          setErrorKind('error')
          setError(formatApiError(err))
        }
      },
    )
    return () => {
      ignore = true
    }
  }, [selectedBuildingId])

  function toggleEquipment(id: string) {
    setSelectedEquipmentIds((prev) => (prev.includes(id) ? prev.filter((e) => e !== id) : [...prev, id]))
  }

  function updateSeatingArrangement(index: number, patch: Partial<SeatingArrangement>) {
    setSeatingArrangements((prev) => prev.map((row, i) => (i === index ? { ...row, ...patch } : row)))
  }

  function removeSeatingArrangement(index: number) {
    setSeatingArrangements((prev) => prev.filter((_, i) => i !== index))
  }

  function addSeatingArrangement() {
    setSeatingArrangements((prev) => [...prev, { name: '', maxCapacity: 0 }])
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError(null)
    setErrorKind('error')

    if (seatingArrangements.length === 0) {
      setError('Ein Raum braucht mindestens eine Sitzordnung.')
      return
    }

    const payload = {
      name,
      floorId: selectedFloorId,
      seatingArrangements: seatingArrangements.map((s) => ({ name: s.name, maxCapacity: Number(s.maxCapacity) })),
      equipmentTypeIds: selectedEquipmentIds,
      notBarrierFree,
    }

    setSubmitting(true)
    try {
      const saved = room
        ? await updateRoom(room.id, { ...payload, version: room.version })
        : await createRoom(payload)
      onSaved(saved)
    } catch (err) {
      // A 409 means someone else saved this room since it was loaded (optimistic-locking
      // conflict) — a distinct, recoverable state from a hard validation failure (FR-009).
      setErrorKind(err instanceof ApiError && err.status === 409 ? 'conflict' : 'error')
      setError(formatApiError(err))
    } finally {
      setSubmitting(false)
    }
  }

  async function handleCreateBuilding() {
    setError(null)
    setErrorKind('error')
    try {
      const created = await createBuilding(newBuildingName)
      setNewBuildingName('')
      setShowNewBuildingForm(false)
      setBuildings(await loadBuildings())
      setSelectedBuildingId(created.id)
    } catch (err) {
      setError(formatApiError(err))
    }
  }

  async function handleCreateFloor() {
    setError(null)
    setErrorKind('error')
    try {
      await createFloor(selectedBuildingId, newFloorName)
      setNewFloorName('')
      setShowNewFloorForm(false)
      setFloors(await listFloors(selectedBuildingId, 'active'))
    } catch (err) {
      setError(formatApiError(err))
    }
  }

  return (
    <form onSubmit={handleSubmit} className="panel room-form">
      {error && (
        <p role="alert" className={errorKind === 'conflict' ? 'feedback-conflict' : 'feedback-error'}>
          {error}
        </p>
      )}

      <div>
        <label htmlFor="room-name">Raumname</label>
        <input id="room-name" value={name} onChange={(e) => setName(e.target.value)} required />
      </div>

      <div>
        <label htmlFor="room-building">Gebäude</label>
        <select
          id="room-building"
          value={selectedBuildingId}
          onChange={(e) => {
            setSelectedBuildingId(e.target.value)
            setSelectedFloorId('')
            setFloors([])
          }}
          required
        >
          <option value="" disabled>
            Gebäude wählen
          </option>
          {buildings.map((b) => (
            <option key={b.id} value={b.id}>
              {b.name}
            </option>
          ))}
        </select>
        {showNewBuildingForm ? (
          <div className="inline-form">
            <div>
              <label htmlFor="new-building-name">Name des neuen Gebäudes</label>
              <input
                id="new-building-name"
                value={newBuildingName}
                onChange={(e) => setNewBuildingName(e.target.value)}
              />
            </div>
            <button type="button" onClick={handleCreateBuilding}>
              Gebäude anlegen
            </button>
          </div>
        ) : (
          <button type="button" onClick={() => setShowNewBuildingForm(true)}>
            Neues Gebäude anlegen
          </button>
        )}
      </div>

      <div>
        <label htmlFor="room-floor">Stockwerk</label>
        <select
          id="room-floor"
          value={selectedFloorId}
          onChange={(e) => setSelectedFloorId(e.target.value)}
          disabled={!selectedBuildingId}
          required
        >
          <option value="" disabled>
            Stockwerk wählen
          </option>
          {floors.map((f) => (
            <option key={f.id} value={f.id}>
              {f.name}
            </option>
          ))}
        </select>
        {selectedBuildingId &&
          (showNewFloorForm ? (
            <div className="inline-form">
              <div>
                <label htmlFor="new-floor-name">Name des neuen Stockwerks</label>
                <input id="new-floor-name" value={newFloorName} onChange={(e) => setNewFloorName(e.target.value)} />
              </div>
              <button type="button" onClick={handleCreateFloor}>
                Stockwerk anlegen
              </button>
            </div>
          ) : (
            <button type="button" onClick={() => setShowNewFloorForm(true)}>
              Neues Stockwerk anlegen
            </button>
          ))}
      </div>

      <fieldset>
        <legend>Ausstattung</legend>
        {equipmentTypes.map((type) => (
          <label key={type.id} className="checkbox-label">
            <input
              type="checkbox"
              checked={selectedEquipmentIds.includes(type.id)}
              onChange={() => toggleEquipment(type.id)}
            />
            {type.name}
          </label>
        ))}
      </fieldset>

      <div>
        <label className="checkbox-label">
          <input
            type="checkbox"
            checked={notBarrierFree}
            onChange={(e) => setNotBarrierFree(e.target.checked)}
            aria-describedby="room-not-barrier-free-help"
          />
          Nicht barrierefrei (Ausnahme)
        </label>
        <p id="room-not-barrier-free-help" className="room-form-help">
          Nur setzen, wenn der Raum trotz Erdgeschoss oder Aufzug nicht barrierefrei ist.
        </p>
      </div>

      <fieldset>
        <legend>Sitzordnungen</legend>
        {seatingArrangements.map((row, index) => (
          <div key={index} className="inline-form">
            <div>
              <label htmlFor={`seating-name-${index}`}>{`Name der Sitzordnung (${index + 1})`}</label>
              <input
                id={`seating-name-${index}`}
                value={row.name}
                onChange={(e) => updateSeatingArrangement(index, { name: e.target.value })}
                required
              />
            </div>
            <div>
              <label htmlFor={`seating-capacity-${index}`}>{`Max. Kapazität (${index + 1})`}</label>
              <input
                id={`seating-capacity-${index}`}
                type="number"
                min={1}
                value={row.maxCapacity}
                onChange={(e) => updateSeatingArrangement(index, { maxCapacity: Number(e.target.value) })}
                required
              />
            </div>
            <button type="button" onClick={() => removeSeatingArrangement(index)}>
              {`Sitzordnung entfernen (${index + 1})`}
            </button>
          </div>
        ))}
        <button type="button" onClick={addSeatingArrangement}>
          Sitzordnung hinzufügen
        </button>
      </fieldset>

      <div className="actions">
        <button type="submit" disabled={submitting}>
          {room ? 'Raum speichern' : 'Raum anlegen'}
        </button>
      </div>
    </form>
  )
}

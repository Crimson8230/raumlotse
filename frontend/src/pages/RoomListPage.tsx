import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import {
  deactivateRoom,
  deleteRoom,
  listRooms,
  listSeatingArrangementNames,
  reactivateRoom,
  searchRooms,
} from '../API/rooms'
import { listBuildings } from '../API/buildings'
import { listEquipmentTypes } from '../API/equipmentTypes'
import { formatApiError } from '../API/client'
import { useAdminMode } from '../auth/useAdminMode'
import { RoomSearchPanel } from '../components/RoomSearch/RoomSearchPanel'
import {
  bookingLink,
  parseSearchParams,
  roomLink,
  toApiQuery,
  toSearchParams,
} from '../components/RoomSearch/roomSearchParams'
import type { Building, EquipmentType, Room, RoomSearchFormState, StatusFilter } from '../types/room'
import './RoomListPage.css'
import { entityStatusLabel } from '../utils/labels'

export default function RoomListPage() {
  const [rooms, setRooms] = useState<Room[]>([])
  const { adminMode } = useAdminMode()
  const [statusFilter, setStatus] = useState<StatusFilter>('active')
  // Without administration mode the list is the booking overview: active rooms only (feature 013, FR-002).
  const status: StatusFilter = adminMode ? statusFilter : 'active'
  const [error, setError] = useState<string | null>(null)
  const [buildings, setBuildings] = useState<Building[]>([])
  const [equipmentTypes, setEquipmentTypes] = useState<EquipmentType[]>([])
  const [seatingArrangementNames, setSeatingArrangementNames] = useState<string[]>([])
  const [searchParams, setSearchParams] = useSearchParams()

  // Search filters live in the URL (contracts/ui.md §1), so reload, back/forward and shared links restore them.
  const searchKey = searchParams.toString()
  const searchState = useMemo(() => parseSearchParams(new URLSearchParams(searchKey)), [searchKey])
  const searching = status === 'active'

  const fetchRooms = useCallback(
    () => (searching ? searchRooms(toApiQuery(searchState)) : listRooms(status)),
    [searching, searchState, status],
  )

  const reload = useCallback(async () => {
    setRooms(await fetchRooms())
  }, [fetchRooms])

  useEffect(() => {
    // `ignore` also discards a superseded search whose response arrives after a newer one.
    let ignore = false
    fetchRooms().then(
      (loaded) => {
        if (!ignore) setRooms(loaded)
      },
      (err) => {
        if (!ignore) setError(formatApiError(err))
      },
    )
    return () => {
      ignore = true
    }
  }, [fetchRooms])

  useEffect(() => {
    let ignore = false
    // All equipment types are needed to name deactivated equipment still assigned to rooms.
    Promise.all([listBuildings('active'), listEquipmentTypes('all'), listSeatingArrangementNames()]).then(
      ([loadedBuildings, loadedEquipmentTypes, loadedNames]) => {
        if (ignore) return
        setBuildings(loadedBuildings)
        setEquipmentTypes(loadedEquipmentTypes)
        setSeatingArrangementNames(loadedNames)
      },
      (err) => {
        if (!ignore) setError(formatApiError(err))
      },
    )
    return () => {
      ignore = true
    }
  }, [])

  const equipmentNames = useMemo(
    () => Object.fromEntries(equipmentTypes.map((type) => [type.id, type.name])),
    [equipmentTypes],
  )
  const activeEquipmentTypes = useMemo(
    () => equipmentTypes.filter((type) => type.status === 'ACTIVE'),
    [equipmentTypes],
  )

  async function guarded(action: () => Promise<unknown>) {
    setError(null)
    try {
      await action()
      await reload()
    } catch (err) {
      setError(formatApiError(err))
    }
  }

  function handleSearch(state: RoomSearchFormState) {
    setError(null)
    setSearchParams(toSearchParams(state))
  }

  function handleReset() {
    setError(null)
    setSearchParams(new URLSearchParams())
  }

  return (
    <main>
      <h1>Räume</h1>
      {error && (
        <p role="alert" className="feedback-error">
          {error}
        </p>
      )}

      {adminMode && (
        <div className="room-list-toolbar">
          <div>
            <label htmlFor="room-status-filter">Status</label>
            <select
              id="room-status-filter"
              value={status}
              onChange={(e) => setStatus(e.target.value as StatusFilter)}
            >
              <option value="active">Aktiv</option>
              <option value="deactivated">Deaktiviert</option>
              <option value="all">Alle</option>
            </select>
          </div>

          <Link to="/admin/rooms/new">Neuer Raum</Link>
        </div>
      )}

      <RoomSearchPanel
        key={searchKey}
        value={searchState}
        buildings={buildings}
        seatingArrangementNames={seatingArrangementNames}
        equipmentTypes={activeEquipmentTypes}
        onSearch={handleSearch}
        onReset={handleReset}
        disabled={!searching}
      />

      {rooms.length === 0 ? (
        searching ? (
          <div className="status-empty">
            <p>Keine passenden Räume gefunden.</p>
            <button type="button" onClick={handleReset}>
              Filter zurücksetzen
            </button>
          </div>
        ) : (
          <p className="status-empty">Keine Räume mit diesem Status.</p>
        )
      ) : (
        <ul className="list-plain">
          {rooms.map((room) => (
            <li key={room.id} className="panel room-list-item">
              <div>
                <Link className="room-list-name" to={roomLink(room.id, searchState)}>{room.name}</Link>
                <span>{room.building.name}</span>
                <span>{room.floor.name}</span>
                <span className={room.status === 'ACTIVE' ? 'status-active' : 'status-deactivated'}>
                  {entityStatusLabel(room.status)}
                </span>
                <span className="room-list-detail">
                  {room.seatingArrangements.map((s) => `${s.name} (max ${s.maxCapacity})`).join(', ')}
                </span>
                {room.barrierFreeReachable && <span className="room-list-detail">Barrierefrei erreichbar</span>}
                {room.equipmentTypeIds.length > 0 && (
                  <span className="room-list-detail">
                    {room.equipmentTypeIds.map((id) => equipmentNames[id] ?? id).join(', ')}
                  </span>
                )}
              </div>

              <div className="actions">
                {room.status === 'ACTIVE' && (
                  <Link
                    to={bookingLink(room.id, searchState)}
                    className="room-list-book"
                    aria-label={`${room.name} buchen`}
                  >
                    Buchen
                  </Link>
                )}
                {adminMode && (
                  <>
                    {room.status === 'ACTIVE' ? (
                      <button type="button" onClick={() => guarded(() => deactivateRoom(room.id))} aria-label={`${room.name} deaktivieren`}>Deaktivieren</button>
                    ) : (
                      <button type="button" onClick={() => guarded(() => reactivateRoom(room.id))} aria-label={`${room.name} reaktivieren`}>Reaktivieren</button>
                    )}
                    <button type="button" onClick={() => guarded(() => deleteRoom(room.id))} aria-label={`${room.name} löschen`}>Löschen</button>
                  </>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
    </main>
  )
}

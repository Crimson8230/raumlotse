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
import { listFavoriteRooms } from '../API/favoriteRooms'
import { formatApiError } from '../API/client'
import { useAdminMode } from '../auth/useAdminMode'
import { FavoriteToggle } from '../components/FavoriteToggle/FavoriteToggle'
import { RoomRow } from '../components/RoomList/RoomRow'
import { RoomSearchPanel } from '../components/RoomSearch/RoomSearchPanel'
import {
  parseSearchParams,
  toApiQuery,
  toSearchParams,
} from '../components/RoomSearch/roomSearchParams'
import type { Building, EquipmentType, FavoriteRoom, Room, RoomSearchFormState, StatusFilter } from '../types/room'
import './RoomListPage.css'
import { ErrorMessage } from '../components/ui'

export default function RoomListPage() {
  const [rooms, setRooms] = useState<Room[]>([])
  const { adminMode, permissions } = useAdminMode()
  const roomManage = adminMode && permissions.includes('ROOM_MANAGE')
  const [statusFilter, setStatus] = useState<StatusFilter>('active')
  // Without administration mode the list is the booking overview: active rooms only (feature 013, FR-002).
  const status: StatusFilter = roomManage ? statusFilter : 'active'
  const [error, setError] = useState<string | null>(null)
  const [buildings, setBuildings] = useState<Building[]>([])
  const [equipmentTypes, setEquipmentTypes] = useState<EquipmentType[]>([])
  const [seatingArrangementNames, setSeatingArrangementNames] = useState<string[]>([])
  // Null until loaded, so "no favorites yet" is not shown while the list is still on its way (feature 017).
  const [favorites, setFavorites] = useState<FavoriteRoom[] | null>(null)
  const [searchParams, setSearchParams] = useSearchParams()
  // Minutentakt für die "Jetzt"-Marke der Streifen; die Zeilen laden veraltete Zeitfenster dabei nach.
  const [now, setNow] = useState(() => new Date())
  useEffect(() => {
    const timer = window.setInterval(() => setNow(new Date()), 60_000)
    return () => window.clearInterval(timer)
  }, [])

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

  useEffect(() => {
    let ignore = false
    listFavoriteRooms().then(
      (loaded) => {
        if (!ignore) setFavorites(loaded)
      },
      (err) => {
        if (!ignore) setError(formatApiError(err))
      },
    )
    return () => {
      ignore = true
    }
  }, [])

  const favoriteIds = useMemo(() => new Set((favorites ?? []).map((f) => f.roomId)), [favorites])
  const unavailableFavorites = useMemo(() => (favorites ?? []).filter((f) => !f.available), [favorites])
  const favoritesOnly = searching && searchState.favoritesOnly

  // A changed favorite changes the ranking: reload favorites and re-run the current search (FR-011).
  async function handleFavoriteChanged() {
    setError(null)
    try {
      const [loadedFavorites] = await Promise.all([listFavoriteRooms(), reload()])
      setFavorites(loadedFavorites)
    } catch (err) {
      setError(formatApiError(err))
    }
  }

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
    <main id="inhalt">
      <h1>Räume</h1>
      {error && (
        <ErrorMessage>{error}</ErrorMessage>
      )}

      {roomManage && (
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
        favoritesOnly && favorites?.length === 0 ? (
          <p className="status-empty">
            Du hast noch keine Favoriten. Markiere Räume mit dem Stern, um sie hier zu sehen.
          </p>
        ) : searching ? (
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
        <>
          {searching && <p className="room-list-sort-note">Sortiert nach Eignung</p>}
          <ul className="list-plain">
            {rooms.map((room, index) => (
              <RoomRow
                key={room.id}
                room={room}
                index={index}
                now={now}
                isFavorite={favoriteIds.has(room.id)}
                searchState={searchState}
                canReserve={permissions.includes('RESERVE')}
                roomManage={roomManage}
                equipmentNames={equipmentNames}
                onFavoriteChanged={handleFavoriteChanged}
                onDeactivate={() => guarded(() => deactivateRoom(room.id))}
                onReactivate={() => guarded(() => reactivateRoom(room.id))}
                onDelete={() => guarded(() => deleteRoom(room.id))}
              />
            ))}
          </ul>
        </>
      )}

      {favoritesOnly && unavailableFavorites.length > 0 && (
        <section aria-labelledby="unavailable-favorites-heading" className="room-list-unavailable">
          <h2 id="unavailable-favorites-heading">Nicht verfügbar</h2>
          <ul className="list-plain">
            {unavailableFavorites.map((favorite) => (
              <li key={favorite.roomId} className="panel room-list-item">
                <div>
                  <FavoriteToggle
                    roomId={favorite.roomId}
                    roomName={favorite.roomName}
                    isFavorite
                    onChanged={handleFavoriteChanged}
                  />
                  <span>{`${favorite.buildingName} · ${favorite.floorName} · ${favorite.roomName}`}</span>
                </div>
              </li>
            ))}
          </ul>
        </section>
      )}
    </main>
  )
}

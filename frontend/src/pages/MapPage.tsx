import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { formatApiError } from '../API/client'
import {
  deleteMap,
  getMap,
  listConnections,
  listMaps,
  listUnplacedRooms,
  placeRoom,
  putConnectionPoint,
  removePlacement,
} from '../API/maps'
import { useCurrentRoles } from '../auth/useCurrentRoles'
import { ConnectionCatalog } from '../components/ConnectionCatalog/ConnectionCatalog'
import { ConnectionLayer } from '../components/FloorMap/ConnectionLayer'
import { MapCanvas } from '../components/FloorMap/MapCanvas'
import { MapSelector } from '../components/FloorMap/MapSelector'
import { UnplacedRoomList } from '../components/FloorMap/UnplacedRoomList'
import { MapUploadControl } from '../components/FloorMap/MapUploadControl'
import type { Connection, MapDetail, MapSummary, Position, RoomRef } from '../types/map'
import './MapPage.css'

export default function MapPage() {
  const { mapId } = useParams()
  const navigate = useNavigate()
  const { admin } = useCurrentRoles()
  const [maps, setMaps] = useState<MapSummary[]>()
  const [loaded, setLoaded] = useState<MapDetail>()
  const [loadedUnplaced, setLoadedUnplaced] = useState<{ mapId: string; rooms: RoomRef[] }>()
  const [activeRoomId, setActiveRoomId] = useState<string>()
  const [activeConnectionId, setActiveConnectionId] = useState<string>()
  const [connections, setConnections] = useState<Connection[]>()
  const [error, setError] = useState<string>()
  const [reloadKey, setReloadKey] = useState(0)

  const selectedId = mapId ?? maps?.[0]?.id
  // Only show the detail that belongs to the current selection; a stale one is dropped without an effect.
  const detail = loaded?.id === selectedId ? loaded : undefined
  const unplaced = loadedUnplaced?.mapId === selectedId ? loadedUnplaced?.rooms : undefined

  useEffect(() => {
    // Rooms are managed elsewhere: refresh when the user comes back to this window or tab.
    const refresh = () => setReloadKey((key) => key + 1)
    window.addEventListener('focus', refresh)
    return () => window.removeEventListener('focus', refresh)
  }, [])

  useEffect(() => {
    let active = true
    listMaps()
      .then((result) => active && setMaps(result))
      .catch((err: unknown) => active && setError(formatApiError(err)))
    return () => {
      active = false
    }
  }, [reloadKey])

  useEffect(() => {
    if (!selectedId) return
    let active = true
    getMap(selectedId)
      .then((result) => active && setLoaded(result))
      .catch((err: unknown) => active && setError(formatApiError(err)))
    return () => {
      active = false
    }
  }, [selectedId, reloadKey])

  useEffect(() => {
    if (!selectedId) return
    let active = true
    listUnplacedRooms(selectedId)
      .then((rooms) => active && setLoadedUnplaced({ mapId: selectedId, rooms }))
      .catch((err: unknown) => active && setError(formatApiError(err)))
    return () => {
      active = false
    }
  }, [selectedId, reloadKey])

  useEffect(() => {
    let active = true
    listConnections()
      .then((result) => active && setConnections(result))
      .catch((err: unknown) => active && setError(formatApiError(err)))
    return () => {
      active = false
    }
  }, [reloadKey])

  function selectRoom(roomId: string | undefined) {
    setActiveConnectionId(undefined)
    setActiveRoomId(roomId)
  }

  function selectConnection(connectionId: string | undefined) {
    setActiveRoomId(undefined)
    setActiveConnectionId(connectionId)
  }

  async function handlePlace(position: Position) {
    if (!selectedId) return
    if (activeConnectionId) {
      setError(undefined)
      try {
        await putConnectionPoint(activeConnectionId, selectedId, position)
        setActiveConnectionId(undefined)
        setReloadKey((key) => key + 1)
      } catch (err) {
        setError(formatApiError(err))
      }
    } else if (activeRoomId) {
      await applyPlacement(activeRoomId, position)
    }
  }

  async function applyPlacement(roomId: string, position: Position) {
    if (!selectedId) return
    setError(undefined)
    try {
      await placeRoom(selectedId, roomId, position)
      setActiveRoomId(undefined)
      setReloadKey((key) => key + 1)
    } catch (err) {
      setError(formatApiError(err))
    }
  }

  async function handleRemovePlacement(roomId: string) {
    if (!selectedId) return
    setError(undefined)
    try {
      await removePlacement(selectedId, roomId)
      setReloadKey((key) => key + 1)
    } catch (err) {
      setError(formatApiError(err))
    }
  }

  function handleSaved(saved: MapSummary) {
    setReloadKey((key) => key + 1)
    navigate(`/maps/${saved.id}`)
  }

  async function handleDelete() {
    if (!detail || !window.confirm(`Karte „${detail.name}“ samt Platzierungen und Verbindungspunkten löschen?`)) return
    try {
      await deleteMap(detail.id)
      setReloadKey((key) => key + 1)
      navigate('/maps')
    } catch (err) {
      setError(formatApiError(err))
    }
  }

  const current = maps?.find((candidate) => candidate.id === selectedId)

  return (
    <main className="map-page">
      <h1>Karten</h1>
      {error && <p role="alert">{error}</p>}
      {maps && (
        <MapSelector
          maps={maps}
          selectedId={selectedId}
          onSelect={(id) => {
            selectRoom(undefined)
            navigate(`/maps/${id}`)
          }}
        />
      )}
      {detail && (
        <MapCanvas
          map={detail}
          placements={detail.placements}
          editable={admin}
          activeRoomId={activeRoomId}
          placementActive={activeConnectionId !== undefined}
          onPlace={(position) => void handlePlace(position)}
          onMove={(roomId, position) => void applyPlacement(roomId, position)}
          onRemove={(roomId) => void handleRemovePlacement(roomId)}
        >
          {maps && (
            <ConnectionLayer
              connections={detail.connections}
              currentMapId={detail.id}
              maps={maps}
              onNavigate={(id) => {
                selectConnection(undefined)
                navigate(`/maps/${id}`)
              }}
            />
          )}
        </MapCanvas>
      )}
      {detail && unplaced && (
        <section aria-label="Platzierung">
          <h2>Nicht platzierte Räume</h2>
          <UnplacedRoomList rooms={unplaced} activeRoomId={activeRoomId} canSelect={admin} onSelect={selectRoom} />
        </section>
      )}
      {detail && connections && (
        <ConnectionCatalog
          connections={connections}
          currentMapId={detail.id}
          admin={admin}
          activeConnectionId={activeConnectionId}
          onSelectForPlacement={selectConnection}
          onChanged={() => setReloadKey((key) => key + 1)}
        />
      )}
      {admin && maps && (
        <section className="map-admin" aria-label="Kartenverwaltung">
          {current && (
            <>
              <MapUploadControl map={current} existingMaps={maps} onSaved={handleSaved} />
              <button type="button" onClick={handleDelete}>
                Karte löschen
              </button>
            </>
          )}
          <h2>Neue Karte</h2>
          <MapUploadControl existingMaps={maps} onSaved={handleSaved} />
        </section>
      )}
    </main>
  )
}

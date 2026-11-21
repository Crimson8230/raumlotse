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
import {
  addSegment,
  addWaypoint,
  attachConnectionPoint,
  attachRoom,
  deleteReferenceSegment,
  deleteSegment,
  deleteWaypoint,
  detachConnectionPoint,
  detachRoom,
  moveWaypoint,
  putReferenceSegment,
  splitSegment,
} from '../API/pathNetwork'
import { ConnectionCatalog } from '../components/ConnectionCatalog/ConnectionCatalog'
import { AttachmentPanel } from '../components/PathNetwork/AttachmentPanel'
import { IssueList } from '../components/PathNetwork/IssueList'
import { PathOverlay } from '../components/PathNetwork/PathOverlay'
import { PathToolbar } from '../components/PathNetwork/PathToolbar'
import { ReferenceSegmentTool } from '../components/PathNetwork/ReferenceSegmentTool'
import { usePathNetwork } from '../components/PathNetwork/usePathNetwork'
import { ConnectionLayer } from '../components/FloorMap/ConnectionLayer'
import { MapCanvas } from '../components/FloorMap/MapCanvas'
import { MapSelector } from '../components/FloorMap/MapSelector'
import { UnplacedRoomList } from '../components/FloorMap/UnplacedRoomList'
import { MapUploadControl } from '../components/FloorMap/MapUploadControl'
import type { Connection, MapDetail, MapSummary, Position, RoomRef } from '../types/map'
import type { AttachTarget, PathTool, ReferenceSegmentInput } from '../types/pathNetwork'
import { useCurrentRoles } from '../auth/useCurrentRoles'
import './MapPage.css'
import { ErrorMessage } from '../components/ui'

/**
 * `editable` is set by the `/admin/maps` routes only; those are guarded by administration mode, so the page itself
 * does not decide on rights. Without it the page is the read-only map overview.
 */
export default function MapPage({ editable = false }: { editable?: boolean }) {
  const base = editable ? '/admin/maps' : '/maps'
  const { mapId } = useParams()
  const navigate = useNavigate()
  const { permissions, refreshing, lastKnownPermissions } = useCurrentRoles()
  const canPlace = editable && permissions.includes('ROOM_PLACEMENT_MANAGE')
  const canManageMaps = editable && permissions.includes('MAP_MANAGE')
  // Choosing a file closes the file dialog and refocuses the window, which re-checks the roles. While that runs the
  // upload form stays mounted, otherwise the chosen file would be thrown away ("Keine Datei ausgewählt").
  const showMapAdmin = canManageMaps || (editable && refreshing && lastKnownPermissions.includes('MAP_MANAGE'))
  const canManageConnections = editable && permissions.includes('CONNECTION_MANAGE')
  // Path network edits are coupled to MAP_MANAGE (feature 023); everyone signed in may view the network.
  const canEditPaths = editable && permissions.includes('MAP_MANAGE')
  const [maps, setMaps] = useState<MapSummary[]>()
  const [loaded, setLoaded] = useState<MapDetail>()
  const [loadedUnplaced, setLoadedUnplaced] = useState<{ mapId: string; rooms: RoomRef[] }>()
  const [activeRoomId, setActiveRoomId] = useState<string>()
  const [activeConnectionId, setActiveConnectionId] = useState<string>()
  const [connections, setConnections] = useState<Connection[]>()
  const [error, setError] = useState<string>()
  const [reloadKey, setReloadKey] = useState(0)
  const [showPaths, setShowPaths] = useState(false)
  const [pathTool, setPathTool] = useState<PathTool>('view')
  const [pendingFromId, setPendingFromId] = useState<string>()
  const [referenceDraft, setReferenceDraft] = useState<Position[]>([])
  const [attachTarget, setAttachTarget] = useState<AttachTarget>()
  const [highlightParts, setHighlightParts] = useState(false)
  const [focusTarget, setFocusTarget] = useState<AttachTarget>()
  const [pathError, setPathError] = useState<string>()

  const selectedId = mapId ?? maps?.[0]?.id
  const paths = usePathNetwork(selectedId, showPaths)
  const pathEditing = showPaths && canEditPaths
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
    if (!selectedId || !canPlace) return
    let active = true
    listUnplacedRooms(selectedId)
      .then((rooms) => active && setLoadedUnplaced({ mapId: selectedId, rooms }))
      .catch((err: unknown) => active && setError(formatApiError(err)))
    return () => {
      active = false
    }
  }, [selectedId, reloadKey, canPlace])

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

  function selectPathTool(tool: PathTool) {
    setPathTool(tool)
    setPendingFromId(undefined)
    setReferenceDraft([])
    setAttachTarget(undefined)
    if (tool !== 'view') {
      setActiveRoomId(undefined)
      setActiveConnectionId(undefined)
    }
  }

  async function runPath(action: () => Promise<unknown>) {
    setPathError(undefined)
    try {
      await action()
      paths.reload()
    } catch (err) {
      setPathError(formatApiError(err))
    }
  }

  function handleSelectAttachTarget(target: AttachTarget | undefined) {
    setPathTool(target ? 'attach' : 'view')
    setPendingFromId(undefined)
    setReferenceDraft([])
    setActiveRoomId(undefined)
    setActiveConnectionId(undefined)
    setAttachTarget(target)
  }

  function handleWaypointClick(waypointId: string) {
    if (!pathEditing || !selectedId) return
    if (pathTool === 'connect') {
      if (!pendingFromId) {
        setPendingFromId(waypointId)
      } else if (pendingFromId === waypointId) {
        setPendingFromId(undefined)
      } else {
        const from = pendingFromId
        setPendingFromId(undefined)
        void runPath(() => addSegment(selectedId, from, waypointId))
      }
    } else if (pathTool === 'delete') {
      void runPath(() => deleteWaypoint(selectedId, waypointId))
    } else if (pathTool === 'attach' && attachTarget) {
      const target = attachTarget
      setAttachTarget(undefined)
      void runPath(() =>
        target.kind === 'ROOM'
          ? attachRoom(selectedId, target.id, waypointId)
          : attachConnectionPoint(selectedId, target.id, waypointId),
      )
    }
  }

  function handleSegmentClick(segmentId: string, position: Position) {
    if (!pathEditing || !selectedId) return
    if (pathTool === 'split') void runPath(() => splitSegment(selectedId, segmentId, position))
    if (pathTool === 'delete') void runPath(() => deleteSegment(selectedId, segmentId))
  }

  function handleSaveReference(input: ReferenceSegmentInput) {
    if (!selectedId) return
    void runPath(async () => {
      await putReferenceSegment(selectedId, input)
      setReferenceDraft([])
    })
  }

  function handleDetach(target: AttachTarget) {
    if (!selectedId) return
    void runPath(() =>
      target.kind === 'ROOM' ? detachRoom(selectedId, target.id) : detachConnectionPoint(selectedId, target.id),
    )
  }

  async function handlePlace(position: Position) {
    if (!selectedId) return
    if (pathEditing && pathTool === 'add') {
      void runPath(() => addWaypoint(selectedId, position))
      return
    }
    if (pathEditing && pathTool === 'reference') {
      setReferenceDraft((draft) => (draft.length >= 2 ? [position] : [...draft, position]))
      return
    }
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
    navigate(`${base}/${saved.id}`)
  }

  async function handleDelete() {
    if (!detail || !window.confirm(`Karte „${detail.name}“ samt Platzierungen und Verbindungspunkten löschen?`)) return
    try {
      await deleteMap(detail.id)
      setReloadKey((key) => key + 1)
      navigate(base)
    } catch (err) {
      setError(formatApiError(err))
    }
  }

  const current = maps?.find((candidate) => candidate.id === selectedId)

  const overlayRooms = (detail?.placements ?? []).map((placement) => ({
    id: placement.room.id,
    label: placement.room.name,
    x: placement.x,
    y: placement.y,
  }))
  const overlayPoints = (detail?.connections ?? []).flatMap((connection) =>
    connection.points
      .filter((point) => point.mapId === detail?.id)
      .map((point) => ({ id: point.id, label: connection.name, x: point.x, y: point.y })),
  )
  const issueNames = {
    rooms: Object.fromEntries(overlayRooms.map((room) => [room.id, room.label])),
    points: Object.fromEntries(overlayPoints.map((point) => [point.id, point.label])),
  }
  const fullyAttached = paths.groups
    ? Object.fromEntries(paths.groups.map((group) => [group.connectionId, group.fullyAttached]))
    : undefined
  const focusWaypointId =
    focusTarget?.kind === 'ROOM'
      ? paths.network?.roomAttachments.find((attachment) => attachment.roomId === focusTarget.id)?.waypointId
      : paths.network?.connectionPointAttachments.find((attachment) => attachment.connectionPointId === focusTarget?.id)
          ?.waypointId

  return (
    <main id="inhalt" className="map-page">
      <h1>{editable ? 'Karten bearbeiten' : 'Karten'}</h1>
      {error && <ErrorMessage>{error}</ErrorMessage>}
      {maps && (
        <MapSelector
          maps={maps}
          selectedId={selectedId}
          onSelect={(id) => {
            selectRoom(undefined)
            selectPathTool('view')
            navigate(`${base}/${id}`)
          }}
        />
      )}
      {detail && (
        <MapCanvas
          map={detail}
          placements={detail.placements}
          editable={canPlace}
          activeRoomId={activeRoomId}
          placementActive={
            activeConnectionId !== undefined || (pathEditing && (pathTool === 'add' || pathTool === 'reference'))
          }
          onPlace={(position) => void handlePlace(position)}
          onMove={canPlace ? (roomId, position) => void applyPlacement(roomId, position) : undefined}
          onRemove={canPlace ? (roomId) => void handleRemovePlacement(roomId) : undefined}
        >
          {maps && (
            <ConnectionLayer
              connections={detail.connections}
              currentMapId={detail.id}
              maps={maps}
              onNavigate={(id) => {
                selectConnection(undefined)
                navigate(`${base}/${id}`)
              }}
            />
          )}
          {showPaths && paths.network && (
            <PathOverlay
              network={paths.network}
              rooms={overlayRooms}
              connectionPoints={overlayPoints}
              tool={pathTool}
              editable={pathEditing}
              pendingFromId={pendingFromId}
              referenceDraft={referenceDraft}
              highlightParts={highlightParts}
              focusWaypointId={focusWaypointId}
              onWaypointClick={handleWaypointClick}
              onWaypointMove={(id, position) => void runPath(() => moveWaypoint(detail.id, id, position))}
              onSegmentClick={handleSegmentClick}
            />
          )}
        </MapCanvas>
      )}
      {detail && (
        <section aria-label="Wegenetz" className="map-paths">
          <h2>Wegenetz</h2>
          <label>
            <input
              type="checkbox"
              checked={showPaths}
              onChange={(event) => {
                setShowPaths(event.target.checked)
                selectPathTool('view')
              }}
            />{' '}
            Wegenetz anzeigen
          </label>
          {showPaths && (pathError ?? paths.error) && <ErrorMessage>{pathError ?? paths.error}</ErrorMessage>}
          {showPaths && paths.network && (
            <>
              {pathEditing && <PathToolbar tool={pathTool === 'attach' ? 'view' : pathTool} onSelect={selectPathTool} />}
              <ReferenceSegmentTool
                network={paths.network}
                active={pathEditing && pathTool === 'reference'}
                draft={referenceDraft}
                canEdit={pathEditing}
                onSave={handleSaveReference}
                onDelete={() => void runPath(() => deleteReferenceSegment(detail.id))}
                onResetDraft={() => setReferenceDraft([])}
              />
              {pathEditing && (
                <>
                  <AttachmentPanel
                    network={paths.network}
                    rooms={overlayRooms}
                    connectionPoints={overlayPoints}
                    canEdit
                    target={attachTarget}
                    onSelectTarget={handleSelectAttachTarget}
                    onDetach={handleDetach}
                  />
                  <IssueList
                    network={paths.network}
                    names={issueNames}
                    highlightParts={highlightParts}
                    onHighlightParts={setHighlightParts}
                    onFocus={setFocusTarget}
                  />
                </>
              )}
            </>
          )}
        </section>
      )}
      {detail && canPlace && unplaced && (
        <section aria-label="Platzierung">
          <h2>Nicht platzierte Räume</h2>
          <UnplacedRoomList rooms={unplaced} activeRoomId={activeRoomId} canSelect={canPlace} onSelect={selectRoom} />
        </section>
      )}
      {detail && connections && (
        <ConnectionCatalog
          connections={connections}
          currentMapId={detail.id}
          admin={canManageConnections}
          activeConnectionId={activeConnectionId}
          onSelectForPlacement={selectConnection}
          onChanged={() => setReloadKey((key) => key + 1)}
          fullyAttached={fullyAttached}
        />
      )}
      {showMapAdmin && maps && (
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

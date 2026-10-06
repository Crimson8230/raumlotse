import { useEffect, useRef, useState, type MouseEvent as ReactMouseEvent, type ReactNode } from 'react'
import { mapImageUrl } from '../../API/maps'
import type { MapSummary, Placement, Position } from '../../types/map'
import './MapCanvas.css'

interface Props {
  map: Pick<MapSummary, 'id' | 'imageVersion' | 'name'>
  placements: Placement[]
  /** Admins may place and move rooms; everyone else only views. */
  editable: boolean
  /** Room that the next click on the map places. */
  activeRoomId?: string
  /** True while another kind of item (e.g. a connection point) is waiting to be placed by the next click. */
  placementActive?: boolean
  onPlace?: (position: Position) => void
  onMove?: (roomId: string, position: Position) => void
  onRemove?: (roomId: string) => void
  /** Overlays that share the image's coordinate space (e.g. connection points). */
  children?: ReactNode
}

const ZOOM_STEP = 0.5
const MAX_ZOOM = 4
const NUDGE = 0.01
const NUDGE_LARGE = 0.05
const ARROWS: Record<string, [number, number]> = {
  ArrowLeft: [-1, 0],
  ArrowRight: [1, 0],
  ArrowUp: [0, -1],
  ArrowDown: [0, 1],
}

function clamp01(value: number): number {
  return Math.min(1, Math.max(0, value))
}

export function MapCanvas({ map, placements, editable, activeRoomId, placementActive = false, onPlace, onMove, onRemove, children }: Props) {
  const planeRef = useRef<HTMLDivElement>(null)
  const [zoom, setZoom] = useState(1)
  const [hint, setHint] = useState<string>()
  const [selectedRoomId, setSelectedRoomId] = useState<string>()
  const [dragging, setDragging] = useState<{ roomId: string; position?: Position }>()
  const draggingId = dragging?.roomId

  /** Converts client coordinates into fractions of the image, or null when they lie outside it. */
  function toPosition(clientX: number, clientY: number, clampToImage = false): Position | null {
    const rect = planeRef.current?.getBoundingClientRect()
    if (!rect || rect.width === 0 || rect.height === 0) return null
    const x = (clientX - rect.left) / rect.width
    const y = (clientY - rect.top) / rect.height
    if (clampToImage) return { x: clamp01(x), y: clamp01(y) }
    return x < 0 || x > 1 || y < 0 || y > 1 ? null : { x, y }
  }

  useEffect(() => {
    if (!draggingId) return
    const handleMove = (event: PointerEvent) => {
      const position = toPosition(event.clientX, event.clientY, true)
      if (position) setDragging({ roomId: draggingId, position })
    }
    const handleUp = (event: PointerEvent) => {
      const position = toPosition(event.clientX, event.clientY, true)
      setDragging(undefined)
      if (position) onMove?.(draggingId, position)
    }
    window.addEventListener('pointermove', handleMove)
    window.addEventListener('pointerup', handleUp)
    return () => {
      window.removeEventListener('pointermove', handleMove)
      window.removeEventListener('pointerup', handleUp)
    }
  }, [draggingId, onMove])

  function handlePlaneClick(event: ReactMouseEvent) {
    if (!editable || !(activeRoomId || placementActive)) return
    const position = toPosition(event.clientX, event.clientY)
    if (!position) {
      setHint('Bitte innerhalb der Karte klicken.')
      return
    }
    setHint(undefined)
    onPlace?.(position)
  }

  const selected = placements.find((placement) => placement.room.id === selectedRoomId)

  return (
    <div className="map-canvas">
      <div className="map-canvas-toolbar" role="toolbar" aria-label="Zoom">
        <button type="button" onClick={() => setZoom((value) => Math.min(MAX_ZOOM, value + ZOOM_STEP))}>
          Vergrößern
        </button>
        <button type="button" onClick={() => setZoom((value) => Math.max(1, value - ZOOM_STEP))}>
          Verkleinern
        </button>
        <button type="button" onClick={() => setZoom(1)}>
          Zurücksetzen
        </button>
      </div>
      {hint && <p role="status">{hint}</p>}
      <div className="map-canvas-viewport">
        <div
          ref={planeRef}
          data-testid="map-plane"
          className={`map-canvas-plane${editable && (activeRoomId || placementActive) ? ' map-canvas-plane--placing' : ''}`}
          style={{ width: `${zoom * 100}%` }}
          onClick={handlePlaneClick}
        >
          <img src={mapImageUrl(map)} alt={`Grundriss ${map.name}`} draggable={false} />
          {placements.map((placement) => {
            const { room } = placement
            const position = dragging?.roomId === room.id && dragging.position ? dragging.position : placement
            const deactivated = room.status === 'DEACTIVATED'
            return (
              <button
                key={room.id}
                type="button"
                className={`map-marker${deactivated ? ' map-marker--deactivated' : ''}`}
                style={{ left: `${position.x * 100}%`, top: `${position.y * 100}%` }}
                aria-label={deactivated ? `${room.name} (deaktiviert)` : room.name}
                onClick={(event) => {
                  event.stopPropagation()
                  setSelectedRoomId(room.id)
                }}
                onKeyDown={(event) => {
                  const direction = ARROWS[event.key]
                  if (!editable || !direction) return
                  event.preventDefault()
                  const step = event.shiftKey ? NUDGE_LARGE : NUDGE
                  onMove?.(room.id, {
                    x: Math.round(clamp01(placement.x + direction[0] * step) * 1000) / 1000,
                    y: Math.round(clamp01(placement.y + direction[1] * step) * 1000) / 1000,
                  })
                }}
                onPointerDown={(event) => {
                  if (!editable) return
                  event.preventDefault()
                  setDragging({ roomId: room.id })
                }}
              >
                <span className="map-marker-label">{room.name}</span>
              </button>
            )
          })}
          {children}
        </div>
      </div>
      {selected && (
        <section aria-label="Raumdetails" className="map-room-details">
          <h2>{selected.room.name}</h2>
          <p>Kennung: {selected.room.id}</p>
          {selected.room.status === 'DEACTIVATED' && <p>Dieser Raum ist deaktiviert.</p>}
          {editable && onRemove && (
            <button
              type="button"
              onClick={() => {
                setSelectedRoomId(undefined)
                onRemove(selected.room.id)
              }}
            >
              Platzierung entfernen
            </button>
          )}
        </section>
      )}
    </div>
  )
}

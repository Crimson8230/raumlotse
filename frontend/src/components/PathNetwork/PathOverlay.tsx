import { useEffect, useRef, useState, type MouseEvent as ReactMouseEvent } from 'react'
import type { Position } from '../../types/map'
import type { MapNetwork, PathTool } from '../../types/pathNetwork'
import './PathOverlay.css'

export interface OverlayPoint {
  id: string
  label: string
  x: number
  y: number
}

interface Props {
  network: MapNetwork
  /** Placed rooms of this map (for attachment lines and unattached markers). */
  rooms: OverlayPoint[]
  /** Connection points of this map. */
  connectionPoints: OverlayPoint[]
  tool: PathTool
  /** Only users who may edit can drag waypoints. */
  editable: boolean
  /** First waypoint of a connection that waits for its second one. */
  pendingFromId?: string
  /** Endpoints clicked so far for the reference segment. */
  referenceDraft: Position[]
  /** Label the separate parts of the network. */
  highlightParts: boolean
  /** Waypoint to emphasize, e.g. chosen from the issue list. */
  focusWaypointId?: string
  onWaypointClick?: (waypointId: string) => void
  onWaypointMove?: (waypointId: string, position: Position) => void
  onSegmentClick?: (segmentId: string, position: Position) => void
}

const meters = new Intl.NumberFormat('de-DE', { maximumFractionDigits: 1 })

function clamp01(value: number): number {
  return Math.min(1, Math.max(0, value))
}

const pct = (value: number) => `${value * 100}%`

/** Walkway network drawn over the map; shares the map plane's 0..1 coordinate space. */
export function PathOverlay({
  network,
  rooms,
  connectionPoints,
  tool,
  editable,
  pendingFromId,
  referenceDraft,
  highlightParts,
  focusWaypointId,
  onWaypointClick,
  onWaypointMove,
  onSegmentClick,
}: Props) {
  const rootRef = useRef<HTMLDivElement>(null)
  const [selectedSegmentId, setSelectedSegmentId] = useState<string>()
  const [dragging, setDragging] = useState<{ id: string; position?: Position }>()
  const draggingId = dragging?.id

  function toPosition(clientX: number, clientY: number): Position | null {
    const rect = rootRef.current?.getBoundingClientRect()
    if (!rect || rect.width === 0 || rect.height === 0) return null
    return { x: clamp01((clientX - rect.left) / rect.width), y: clamp01((clientY - rect.top) / rect.height) }
  }

  useEffect(() => {
    if (!draggingId) return
    const handleMove = (event: PointerEvent) => {
      const position = toPosition(event.clientX, event.clientY)
      if (position) setDragging({ id: draggingId, position })
    }
    const handleUp = (event: PointerEvent) => {
      const position = toPosition(event.clientX, event.clientY)
      setDragging(undefined)
      if (position) onWaypointMove?.(draggingId, position)
    }
    window.addEventListener('pointermove', handleMove)
    window.addEventListener('pointerup', handleUp)
    return () => {
      window.removeEventListener('pointermove', handleMove)
      window.removeEventListener('pointerup', handleUp)
    }
  }, [draggingId, onWaypointMove])

  const waypointPosition = (id: string): Position | undefined => {
    if (dragging?.id === id && dragging.position) return dragging.position
    return network.waypoints.find((waypoint) => waypoint.id === id)
  }
  const partOf = (waypointId: string) => network.issues.components.findIndex((part) => part.includes(waypointId))
  const roomsById = new Map(rooms.map((room) => [room.id, room]))
  const pointsById = new Map(connectionPoints.map((point) => [point.id, point]))
  const selectedSegment = network.segments.find((segment) => segment.id === selectedSegmentId)
  const reference = network.referenceSegment

  function handleSegmentClick(event: ReactMouseEvent, segmentId: string) {
    event.stopPropagation()
    setSelectedSegmentId(segmentId)
    const position = toPosition(event.clientX, event.clientY)
    if (position) onSegmentClick?.(segmentId, position)
  }

  const attachmentLines = [
    ...network.roomAttachments.map((attachment) => ({
      key: `room-${attachment.roomId}`,
      from: roomsById.get(attachment.roomId),
      waypointId: attachment.waypointId,
    })),
    ...network.connectionPointAttachments.map((attachment) => ({
      key: `point-${attachment.connectionPointId}`,
      from: pointsById.get(attachment.connectionPointId),
      waypointId: attachment.waypointId,
    })),
  ]

  return (
    <div ref={rootRef} className="path-overlay" data-testid="path-overlay">
      <svg className="path-overlay-lines" viewBox="0 0 1 1" preserveAspectRatio="none" aria-hidden="true">
        {network.segments.map((segment) => {
          const a = waypointPosition(segment.fromWaypointId)
          const b = waypointPosition(segment.toWaypointId)
          if (!a || !b) return null
          return (
            <g key={segment.id}>
              <line
                className={`path-segment${segment.id === selectedSegmentId ? ' path-segment--selected' : ''}`}
                x1={a.x} y1={a.y} x2={b.x} y2={b.y}
              />
              <line
                className="path-segment-hit"
                data-segment-id={segment.id}
                x1={a.x} y1={a.y} x2={b.x} y2={b.y}
                onClick={(event) => handleSegmentClick(event, segment.id)}
              />
            </g>
          )
        })}
        {attachmentLines.map(({ key, from, waypointId }) => {
          const to = waypointPosition(waypointId)
          if (!from || !to) return null
          return <line key={key} className="path-attachment" x1={from.x} y1={from.y} x2={to.x} y2={to.y} />
        })}
        {reference && (
          <line className="path-reference" x1={reference.x1} y1={reference.y1} x2={reference.x2} y2={reference.y2} />
        )}
        {referenceDraft.length === 2 && (
          <line
            className="path-reference path-reference--draft"
            x1={referenceDraft[0].x} y1={referenceDraft[0].y} x2={referenceDraft[1].x} y2={referenceDraft[1].y}
          />
        )}
      </svg>

      {reference && (
        <span
          className="path-label path-label--reference"
          style={{ left: pct((reference.x1 + reference.x2) / 2), top: pct((reference.y1 + reference.y2) / 2) }}
        >
          {`Referenz: ${meters.format(reference.lengthMeters)} m`}
        </span>
      )}
      {referenceDraft.map((point, index) => (
        <span key={index} className="path-reference-point" style={{ left: pct(point.x), top: pct(point.y) }} />
      ))}

      {selectedSegment && (
        <div
          role="status"
          className="path-label path-label--segment"
          style={{
            left: pct(((waypointPosition(selectedSegment.fromWaypointId)?.x ?? 0) + (waypointPosition(selectedSegment.toWaypointId)?.x ?? 0)) / 2),
            top: pct(((waypointPosition(selectedSegment.fromWaypointId)?.y ?? 0) + (waypointPosition(selectedSegment.toWaypointId)?.y ?? 0)) / 2),
          }}
        >
          {selectedSegment.lengthMeters === null
            ? 'Länge nicht verfügbar (Maßstab fehlt)'
            : `${meters.format(selectedSegment.lengthMeters)} m`}
        </div>
      )}

      {network.waypoints.map((waypoint, index) => {
        const position = waypointPosition(waypoint.id) ?? waypoint
        const part = highlightParts ? partOf(waypoint.id) : -1
        const classes = ['path-waypoint']
        if (waypoint.id === pendingFromId) classes.push('path-waypoint--pending')
        if (waypoint.id === focusWaypointId) classes.push('path-waypoint--focus')
        if (part >= 0) classes.push(`path-waypoint--part-${part % 4}`)
        const suffix = waypoint.id === pendingFromId ? ' (Start der Verbindung)' : part >= 0 ? ` (Netzteil ${part + 1})` : ''
        return (
          <button
            key={waypoint.id}
            type="button"
            className={classes.join(' ')}
            style={{ left: pct(position.x), top: pct(position.y) }}
            aria-label={`Wegpunkt ${index + 1}${suffix}`}
            onClick={(event) => {
              event.stopPropagation()
              if (tool !== 'move') onWaypointClick?.(waypoint.id)
            }}
            onPointerDown={(event) => {
              if (tool !== 'move' || !editable) return
              event.preventDefault()
              setDragging({ id: waypoint.id })
            }}
          />
        )
      })}

      {[...rooms, ...connectionPoints].map((point) => {
        const unattached =
          network.issues.unattachedRoomIds.includes(point.id) || network.issues.unattachedConnectionPointIds.includes(point.id)
        if (!unattached) return null
        return (
          <span
            key={point.id}
            role="img"
            aria-label={`${point.label}: nicht angebunden`}
            className="path-unattached"
            style={{ left: pct(point.x), top: pct(point.y) }}
          >
            !
          </span>
        )
      })}
    </div>
  )
}

import { Wayfinding, type WayDirection } from '../ui'
import type { NeighborDirection, NeighborEntry, NeighborStatus, Neighbors } from '../../types/routing'

const DIRECTIONS: Record<NeighborDirection, WayDirection> = {
  LEFT: 'links',
  RIGHT: 'rechts',
  STRAIGHT: 'geradeaus',
  UP: 'hoch',
  DOWN: 'runter',
}

const STATUS_LABELS: Record<NeighborStatus, string> = {
  AVAILABLE: 'Verfügbar',
  RESERVED: 'Reserviert',
  OCCUPIED: 'Belegt',
}

const MAX_TARGET_LENGTH = 28

function shorten(text: string): string {
  return text.length <= MAX_TARGET_LENGTH ? text : `${text.slice(0, MAX_TARGET_LENGTH - 1).trimEnd()}…`
}

function targetOf(entry: NeighborEntry): string {
  if (entry.kind === 'ROOM') return shorten(entry.name)
  return entry.targetFloorName ? `${shorten(entry.name)} (${entry.targetFloorName})` : shorten(entry.name)
}

function walkingTime(seconds: number): string {
  return seconds < 60 ? `${seconds} s` : `${Math.round(seconds / 60)} Min.`
}

function distanceOf(entry: NeighborEntry): string {
  return `ca. ${Math.round(entry.distanceMeters)} m (${walkingTime(entry.walkingSeconds)})`
}

/** Neighboring rooms and ways up/down shown on the door sign; renders nothing when there is nothing to show. */
export function NeighborSection({ neighbors }: { neighbors: Neighbors | null }) {
  if (!neighbors || !neighbors.available || neighbors.entries.length === 0) {
    return null
  }
  return (
    <section className="room-display-neighbors" aria-labelledby="room-display-neighbors-title">
      <h2 id="room-display-neighbors-title">Nachbarräume</h2>
      <ul className="room-display-neighbor-list">
        {neighbors.entries.map((entry) => (
          <li key={`${entry.direction}-${entry.roomId ?? entry.connectionId}`} className="room-display-neighbor">
            <Wayfinding direction={DIRECTIONS[entry.direction]} target={targetOf(entry)} distance={distanceOf(entry)} />
            {entry.status && (
              <span className={`room-display-neighbor-status room-display-neighbor-status-${entry.status.toLowerCase()}`}>
                {STATUS_LABELS[entry.status]}
              </span>
            )}
          </li>
        ))}
      </ul>
    </section>
  )
}

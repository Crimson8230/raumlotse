import type { RoomRef } from '../../types/map'

interface Props {
  rooms: RoomRef[]
  activeRoomId: string | undefined
  onSelect: (roomId: string) => void
  /** Only admins can pick a room to place; others just see what is missing. */
  canSelect: boolean
}

export function UnplacedRoomList({ rooms, activeRoomId, onSelect, canSelect }: Props) {
  if (rooms.length === 0) {
    return <p className="map-empty">Alle Räume sind platziert.</p>
  }
  return (
    <ul aria-label="Nicht platzierte Räume" className="unplaced-rooms">
      {rooms.map((room) => (
        <li key={room.id}>
          {canSelect ? (
            <button type="button" aria-pressed={room.id === activeRoomId} onClick={() => onSelect(room.id)}>
              {room.name}
            </button>
          ) : (
            room.name
          )}
        </li>
      ))}
    </ul>
  )
}

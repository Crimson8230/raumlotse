import { Link } from 'react-router-dom'
import { FavoriteToggle } from '../FavoriteToggle/FavoriteToggle'
import { bookingLink, roomLink } from '../RoomSearch/roomSearchParams'
import { Strip } from '../ui'
import type { Room, RoomSearchFormState } from '../../types/room'
import { entityStatusLabel } from '../../utils/labels'
import { dayRange } from './dayWindows'
import { useBecameVisible, useRoomDayWindows } from './useRoomDayWindows'
import './RoomRow.css'

interface RoomRowProps {
  room: Room
  /** Position in der Liste; ohne IntersectionObserver werden nur die ersten Zeilen geladen. */
  index: number
  now: Date
  isFavorite: boolean
  searchState: RoomSearchFormState
  canReserve: boolean
  /** Administrationsmodus mit Raumverwaltung: zeigt die Gruppe "Verwalten". */
  roomManage: boolean
  equipmentNames: Record<string, string>
  onFavoriteChanged: () => void
  onDeactivate: () => void
  onReactivate: () => void
  onDelete: () => void
}

/**
 * Eine Raumzeile der Raumliste (Feature 019): Favorit als Zustand, Name in Serif, Tagesstreifen mit "Jetzt",
 * genau eine Hauptaktion; Verwaltungsaktionen stehen abgesetzt und sekundär in der Gruppe "Verwalten".
 */
export function RoomRow({
  room,
  index,
  now,
  isFavorite,
  searchState,
  canReserve,
  roomManage,
  equipmentNames,
  onFavoriteChanged,
  onDeactivate,
  onReactivate,
  onDelete,
}: RoomRowProps) {
  const active = room.status === 'ACTIVE'
  const [rowRef, visible] = useBecameVisible(index)
  const strip = useRoomDayWindows(room.id, now, active && visible)
  const { start, end } = dayRange(now)

  return (
    <li ref={rowRef} className="panel room-list-item room-row">
      <div className="room-row__info">
        {active && (
          <FavoriteToggle roomId={room.id} roomName={room.name} isFavorite={isFavorite} onChanged={onFavoriteChanged} />
        )}
        <Link className="room-list-name room-name" to={roomLink(room.id, searchState)}>
          {room.name}
        </Link>
        <span>{room.building.name}</span>
        <span>{room.floor.name}</span>
        <span className={active ? 'status-active' : 'status-deactivated'}>{entityStatusLabel(room.status)}</span>
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

      {active && (
        <div className="room-row__strip">
          {strip.state === 'loading' && strip.windows.length === 0 ? (
            // Ein leerer Streifen würde "ganztägig frei" ansagen, solange die Belegung unbekannt ist.
            <p className="room-row__strip-note" role="status">Belegung lädt …</p>
          ) : strip.state === 'failed' && strip.windows.length === 0 ? (
            <p className="room-row__strip-note">Belegung nicht verfügbar</p>
          ) : (
            <>
              <Strip rangeStart={start} rangeEnd={end} windows={strip.windows} now={now} />
              {strip.state === 'failed' && <p className="room-row__strip-note">Belegung nicht aktuell</p>}
            </>
          )}
        </div>
      )}

      <div className="room-row__actions actions">
        {active &&
          (canReserve ? (
            <Link
              to={bookingLink(room.id, searchState)}
              className="btn-primary room-list-book"
              aria-label={`${room.name} buchen`}
            >
              Buchen
            </Link>
          ) : (
            <Link
              to={roomLink(room.id, searchState)}
              className="room-list-open"
              aria-label={`${room.name} öffnen`}
            >
              Öffnen
            </Link>
          ))}
        {roomManage && (
          <div role="group" aria-label="Verwalten" className="room-row__manage">
            {active ? (
              <button type="button" onClick={onDeactivate} aria-label={`${room.name} deaktivieren`}>
                Deaktivieren
              </button>
            ) : (
              <button type="button" onClick={onReactivate} aria-label={`${room.name} reaktivieren`}>
                Reaktivieren
              </button>
            )}
            <button type="button" onClick={onDelete} aria-label={`${room.name} löschen`}>
              Löschen
            </button>
          </div>
        )}
      </div>
    </li>
  )
}

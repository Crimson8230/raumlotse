import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { deactivateRoom, deleteRoom, listRooms, reactivateRoom } from '../API/rooms'
import { formatApiError } from '../API/client'
import type { Room, StatusFilter } from '../types/room'

export default function RoomListPage() {
  const [rooms, setRooms] = useState<Room[]>([])
  const [status, setStatus] = useState<StatusFilter>('active')
  const [error, setError] = useState<string | null>(null)

  const fetchRooms = useCallback(() => listRooms(status), [status])

  const reload = useCallback(async () => {
    setRooms(await fetchRooms())
  }, [fetchRooms])

  useEffect(() => {
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

  async function guarded(action: () => Promise<unknown>) {
    setError(null)
    try {
      await action()
      await reload()
    } catch (err) {
      setError(formatApiError(err))
    }
  }

  return (
    <div>
      <h1>Rooms</h1>
      {error && <p role="alert">{error}</p>}

      <label htmlFor="room-status-filter">Status</label>
      <select
        id="room-status-filter"
        value={status}
        onChange={(e) => setStatus(e.target.value as StatusFilter)}
      >
        <option value="active">Active</option>
        <option value="deactivated">Deactivated</option>
        <option value="all">All</option>
      </select>

      <Link to="/rooms/new">New room</Link>

      <ul>
        {rooms.map((room) => (
          <li key={room.id}>
            <Link to={`/rooms/${room.id}`}>{room.name}</Link>
            <span>{room.building.name}</span>
            <span>{room.floor.name}</span>
            <span>{room.status}</span>

            {room.status === 'ACTIVE' ? (
              <button type="button" onClick={() => guarded(() => deactivateRoom(room.id))}>
                {`Deactivate ${room.name}`}
              </button>
            ) : (
              <button type="button" onClick={() => guarded(() => reactivateRoom(room.id))}>
                {`Reactivate ${room.name}`}
              </button>
            )}
            <button type="button" onClick={() => guarded(() => deleteRoom(room.id))}>
              {`Delete ${room.name}`}
            </button>
          </li>
        ))}
      </ul>
    </div>
  )
}

import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { deactivateRoom, deleteRoom, listRooms, reactivateRoom } from '../API/rooms'
import { formatApiError } from '../API/client'
import type { Room, StatusFilter } from '../types/room'
import './RoomListPage.css'

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
    <main>
      <h1>Räume</h1>
      {error && (
        <p role="alert" className="feedback-error">
          {error}
        </p>
      )}

      <div className="room-list-toolbar">
        <div>
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
        </div>

        <Link to="/rooms/new">New room</Link>
      </div>

      {rooms.length === 0 ? (
        <p className="status-empty">No rooms match the selected status.</p>
      ) : (
        <ul className="list-plain">
          {rooms.map((room) => (
            <li key={room.id} className="panel room-list-item">
              <div>
                <Link to={`/rooms/${room.id}`}>{room.name}</Link>
                <span>{room.building.name}</span>
                <span>{room.floor.name}</span>
                <span className={room.status === 'ACTIVE' ? 'status-active' : 'status-deactivated'}>
                  {room.status}
                </span>
              </div>

              <div className="actions">
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
              </div>
            </li>
          ))}
        </ul>
      )}
    </main>
  )
}

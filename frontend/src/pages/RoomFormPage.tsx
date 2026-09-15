import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { RoomForm } from '../components/RoomForm/RoomForm'
import { getRoom } from '../API/rooms'
import type { Room } from '../types/room'

export default function RoomFormPage() {
  const { roomId } = useParams<{ roomId: string }>()
  const navigate = useNavigate()
  const [room, setRoom] = useState<Room | undefined>(undefined)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [loading, setLoading] = useState(Boolean(roomId))

  useEffect(() => {
    if (!roomId) {
      return
    }
    let ignore = false
    getRoom(roomId).then(
      (loaded) => {
        if (!ignore) {
          setRoom(loaded)
          setLoading(false)
        }
      },
      () => {
        if (!ignore) {
          setLoadError('This room could not be found.')
          setLoading(false)
        }
      },
    )
    return () => {
      ignore = true
    }
  }, [roomId])

  if (loading) {
    return (
      <main>
        <p className="status-loading">Loading…</p>
      </main>
    )
  }

  if (loadError) {
    return (
      <main>
        <p role="alert" className="feedback-error">
          {loadError}
        </p>
      </main>
    )
  }

  return (
    <main>
      <h1>{room ? `Edit ${room.name}` : 'New Room'}</h1>
      {room && (
        <dl className="panel">
          <dt>Building</dt>
          <dd>{room.building.name}</dd>
          <dt>Floor</dt>
          <dd>{room.floor.name}</dd>
          <dt>Status</dt>
          <dd className={room.status === 'ACTIVE' ? 'status-active' : 'status-deactivated'}>{room.status}</dd>
        </dl>
      )}
      <RoomForm room={room} onSaved={(saved) => navigate(`/rooms/${saved.id}`)} />
    </main>
  )
}

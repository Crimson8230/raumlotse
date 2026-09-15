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
    return <p>Loading…</p>
  }

  if (loadError) {
    return <p role="alert">{loadError}</p>
  }

  return (
    <div>
      <h1>{room ? `Edit ${room.name}` : 'New Room'}</h1>
      {room && (
        <dl>
          <dt>Building</dt>
          <dd>{room.building.name}</dd>
          <dt>Floor</dt>
          <dd>{room.floor.name}</dd>
          <dt>Status</dt>
          <dd>{room.status}</dd>
        </dl>
      )}
      <RoomForm room={room} onSaved={(saved) => navigate(`/rooms/${saved.id}`)} />
    </div>
  )
}

import { useEffect, useMemo, useState } from 'react'
import { useParams } from 'react-router-dom'
import { getRoom } from '../API/rooms'
import { listRoomReservations } from '../API/reservations'
import { RoomDisplay } from '../components/RoomDisplay/RoomDisplay'
import {
  deriveRoomDisplayStatus,
  selectNextReservation,
  selectCurrentReservation,
} from '../components/RoomDisplay/roomDisplayLogic'
import type { Reservation } from '../types/reservation'
import type { Room } from '../types/room'

export default function RoomDisplayPage() {
  const { roomId } = useParams<{ roomId: string }>()
  const [requestState, setRequestState] = useState<{
    roomId: string | null
    room: Room | null
    reservations: Reservation[]
    errorMessage: string | null
  }>({ roomId: null, room: null, reservations: [], errorMessage: null })
  const [currentDateTime, setCurrentDateTime] = useState(() => new Date())

  useEffect(() => {
    const timer = window.setInterval(() => setCurrentDateTime(new Date()), 30_000)
    return () => window.clearInterval(timer)
  }, [])

  useEffect(() => {
    if (!roomId) {
      return
    }

    let ignore = false
    const loadRoomData = () => {
      Promise.all([getRoom(roomId), listRoomReservations(roomId)]).then(
        ([loadedRoom, loadedReservations]) => {
          if (!ignore) {
            setRequestState({
              roomId,
              room: loadedRoom,
              reservations: loadedReservations,
              errorMessage: null,
            })
          }
        },
        () => {
          if (!ignore) {
            setRequestState({
              roomId,
              room: null,
              reservations: [],
              errorMessage: 'Room information is unavailable.',
            })
          }
        },
      )
    }

    loadRoomData()
    const refreshTimer = window.setInterval(loadRoomData, 30_000)

    return () => {
      ignore = true
      window.clearInterval(refreshTimer)
    }
  }, [roomId])

  const requestMatchesRoom = requestState.roomId === roomId
  const room = requestMatchesRoom ? requestState.room : null
  const errorMessage = requestMatchesRoom ? requestState.errorMessage : null
  const loading = Boolean(roomId) && !requestMatchesRoom

  const currentReservation = useMemo(
    () =>
      room && roomId
        ? selectCurrentReservation(requestState.reservations, currentDateTime, roomId)
        : null,
    [currentDateTime, requestState.reservations, room, roomId],
  )

  const displayStatus = errorMessage
    ? 'UNAVAILABLE'
    : room && roomId
      ? deriveRoomDisplayStatus(requestState.reservations, currentDateTime, roomId)
      : 'UNAVAILABLE'

  const nextReservation = useMemo(
    () =>
      room && roomId
        ? selectNextReservation(requestState.reservations, currentDateTime, roomId)
        : null,
    [currentDateTime, requestState.reservations, room, roomId],
  )

  if (!roomId) {
    return (
      <RoomDisplay
        roomName="Room unavailable"
        currentDateTime={currentDateTime}
        reservation={null}
        status="UNAVAILABLE"
        nextReservation={null}
        state="unavailable"
        errorMessage="Room information is unavailable."
      />
    )
  }

  if (loading) {
    return (
      <main>
        <p className="status-loading">Loading room display…</p>
      </main>
    )
  }

  return (
    <RoomDisplay
      roomName={room?.name ?? 'Room unavailable'}
      currentDateTime={currentDateTime}
      reservation={currentReservation}
      status={displayStatus}
      nextReservation={nextReservation}
      state={errorMessage ? 'unavailable' : currentReservation ? 'reservation' : 'no-reservation'}
      errorMessage={errorMessage ?? undefined}
    />
  )
}

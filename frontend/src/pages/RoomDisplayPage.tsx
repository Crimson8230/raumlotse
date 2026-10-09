import { useEffect, useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { getRoom } from '../API/rooms'
import { listRoomReservations } from '../API/reservations'
import { getRoomStatus } from '../API/roomStatus'
import { RoomDisplay } from '../components/RoomDisplay/RoomDisplay'
import { PresenceSimulateButton } from '../components/PresenceSimulateButton/PresenceSimulateButton'
import {
  deriveRoomDisplayStatus,
  selectNextReservation,
  selectCurrentReservation,
} from '../components/RoomDisplay/roomDisplayLogic'
import type { Reservation } from '../types/reservation'
import type { RoomStatus } from '../types/roomStatus'
import type { Room } from '../types/room'
import { checkInLink } from '../utils/checkInLink'

export default function RoomDisplayPage() {
  const { roomId } = useParams<{ roomId: string }>()
  const [requestState, setRequestState] = useState<{
    roomId: string | null
    room: Room | null
    reservations: Reservation[]
    roomStatus: RoomStatus | null
    errorMessage: string | null
  }>({ roomId: null, room: null, reservations: [], roomStatus: null, errorMessage: null })
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
      // Device icons are optional: a failing status request must not take the whole display down (feature 014).
      Promise.all([getRoom(roomId), listRoomReservations(roomId), getRoomStatus(roomId).catch(() => null)]).then(
        ([loadedRoom, loadedReservations, loadedStatus]) => {
          if (!ignore) {
            setRequestState({
              roomId,
              room: loadedRoom,
              reservations: loadedReservations,
              roomStatus: loadedStatus,
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
              roomStatus: null,
              errorMessage: 'Rauminformationen sind nicht verfügbar.',
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
        roomName="Raum nicht verfügbar"
        currentDateTime={currentDateTime}
        reservation={null}
        status="UNAVAILABLE"
        nextReservation={null}
        state="unavailable"
        errorMessage="Rauminformationen sind nicht verfügbar."
      />
    )
  }

  if (loading) {
    return (
      <main>
        <p className="status-loading">Raumanzeige wird geladen…</p>
      </main>
    )
  }

  return (
    <>
      <p className="room-display-back"><Link to={`/rooms/${roomId}`}>Zurück zum Raum</Link></p>
      <RoomDisplay
        roomName={room?.name ?? 'Raum nicht verfügbar'}
        currentDateTime={currentDateTime}
        reservation={currentReservation}
        status={displayStatus}
        nextReservation={nextReservation}
        state={errorMessage ? 'unavailable' : currentReservation ? 'reservation' : 'no-reservation'}
        errorMessage={errorMessage ?? undefined}
        checkInLink={checkInLink(roomId, 'qr')}
        devices={requestMatchesRoom ? requestState.roomStatus?.devices : null}
        adminControls={
          <PresenceSimulateButton
            roomId={roomId}
            lastPresenceAt={requestMatchesRoom ? requestState.roomStatus?.lastPresenceAt ?? null : null}
          />
        }
      />
    </>
  )
}

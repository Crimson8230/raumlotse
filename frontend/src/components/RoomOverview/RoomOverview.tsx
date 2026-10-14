import type { Room } from '../../types/room'
import './RoomOverview.css'

interface RoomOverviewProps {
  room: Room
  /** Ausstattungsnamen nach Typ-ID; unbekannte IDs erscheinen als ID. */
  equipmentNames: Record<string, string>
}

const NO_DATA = 'keine Angabe'
const yesNo = (value: boolean) => (value ? 'Ja' : 'Nein')

/** "Raum auf einen Blick": Sitzordnungen, Ausstattung und Zugang aus den vorhandenen Raumdaten. */
export function RoomOverview({ room, equipmentNames }: RoomOverviewProps) {
  const seating = room.seatingArrangements.map((s) => `${s.name} (max ${s.maxCapacity})`).join(', ')
  const equipment = room.equipmentTypeIds.map((id) => equipmentNames[id] ?? id).join(', ')

  return (
    <section className="room-overview" aria-labelledby="room-overview-title">
      <h2 id="room-overview-title">Raum auf einen Blick</h2>
      <dl className="panel room-overview__list">
        <dt>Sitzordnungen</dt>
        <dd>{seating || NO_DATA}</dd>
        <dt>Ausstattung</dt>
        <dd>{equipment || NO_DATA}</dd>
        <dt>Zugang</dt>
        <dd>
          <ul className="list-plain">
            <li>Barrierefrei erreichbar: {yesNo(room.barrierFreeReachable)}</li>
            <li>Aufzug im Gebäude: {yesNo(room.building.hasElevator)}</li>
            <li>Ebene ohne Stufen: {yesNo(room.floor.groundFloor)}</li>
          </ul>
        </dd>
      </dl>
    </section>
  )
}

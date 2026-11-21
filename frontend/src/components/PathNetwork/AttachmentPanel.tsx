import type { MapNetwork, AttachTarget } from '../../types/pathNetwork'

interface Item {
  id: string
  label: string
}

interface Props {
  network: MapNetwork
  /** Placed rooms of this map. */
  rooms: Item[]
  connectionPoints: Item[]
  canEdit: boolean
  /** Room or connection point that waits for the next click on a waypoint. */
  target?: AttachTarget
  onSelectTarget: (target: AttachTarget | undefined) => void
  onDetach: (target: AttachTarget) => void
}

/** Shows which rooms and connection points are attached to the network and lets editors (re)attach them. */
export function AttachmentPanel({ network, rooms, connectionPoints, canEdit, target, onSelectTarget, onDetach }: Props) {
  const attachedRooms = new Set(network.roomAttachments.map((attachment) => attachment.roomId))
  const attachedPoints = new Set(network.connectionPointAttachments.map((attachment) => attachment.connectionPointId))

  function row(kind: AttachTarget['kind'], item: Item, attached: boolean) {
    const selected = target?.kind === kind && target.id === item.id
    return (
      <li key={item.id}>
        <span>{item.label}</span> <span>{attached ? 'angebunden' : 'nicht angebunden'}</span>
        {canEdit && (
          <>
            {' '}
            <button
              type="button"
              aria-pressed={selected}
              aria-label={`${item.label} ${attached ? 'neu anbinden' : 'anbinden'}`}
              onClick={() => onSelectTarget(selected ? undefined : { kind, id: item.id })}
            >
              {selected ? 'Anbinden: Wegpunkt wählen' : attached ? 'Neu anbinden' : 'Anbinden'}
            </button>
            {attached && (
              <button type="button" aria-label={`${item.label} lösen`} onClick={() => onDetach({ kind, id: item.id })}>
                Lösen
              </button>
            )}
          </>
        )}
      </li>
    )
  }

  return (
    <section aria-label="Anbindung an das Wegenetz" className="path-attachments">
      <h2>Anbindung an das Wegenetz</h2>
      {canEdit && target && <p>Klicken Sie auf einen Wegpunkt, um die Auswahl anzubinden.</p>}
      <h3>Räume</h3>
      {rooms.length === 0 ? (
        <p>Keine platzierten Räume.</p>
      ) : (
        <ul>{rooms.map((room) => row('ROOM', room, attachedRooms.has(room.id)))}</ul>
      )}
      <h3>Treppen und Aufzüge</h3>
      {connectionPoints.length === 0 ? (
        <p>Keine Verbindungspunkte auf dieser Karte.</p>
      ) : (
        <ul>{connectionPoints.map((point) => row('CONNECTION_POINT', point, attachedPoints.has(point.id)))}</ul>
      )}
    </section>
  )
}

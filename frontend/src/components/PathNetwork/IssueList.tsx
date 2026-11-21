import type { AttachTarget, MapNetwork } from '../../types/pathNetwork'

interface Props {
  network: MapNetwork
  names: { rooms: Record<string, string>; points: Record<string, string> }
  highlightParts: boolean
  onHighlightParts: (highlight: boolean) => void
  /** Emphasize the room or connection point on the map. */
  onFocus: (target: AttachTarget) => void
}

/** Problems of a map's network: missing scale, separate parts, unattached rooms and connection points. */
export function IssueList({ network, names, highlightParts, onHighlightParts, onFocus }: Props) {
  const { issues } = network
  const roomName = (id: string) => names.rooms[id] ?? id
  const pointName = (id: string) => names.points[id] ?? id
  const items: { key: string; text: string; focus?: AttachTarget; name?: string }[] = []

  if (issues.missingScale) items.push({ key: 'scale', text: 'Maßstab fehlt: Streckenlängen sind nicht verfügbar.' })
  if (issues.componentCount > 1) {
    items.push({ key: 'parts', text: `Das Wegenetz besteht aus ${issues.componentCount} getrennten Teilen.` })
  }
  for (const id of issues.unattachedRoomIds) {
    items.push({ key: `room-${id}`, text: `${roomName(id)} ist nicht angebunden.`, focus: { kind: 'ROOM', id }, name: roomName(id) })
  }
  for (const id of issues.unattachedConnectionPointIds) {
    items.push({
      key: `point-${id}`,
      text: `${pointName(id)} ist nicht angebunden.`,
      focus: { kind: 'CONNECTION_POINT', id },
      name: pointName(id),
    })
  }
  for (const outside of issues.attachmentsOutsideMainComponent) {
    const name = outside.kind === 'ROOM' ? roomName(outside.id) : pointName(outside.id)
    items.push({
      key: `outside-${outside.id}`,
      text: `${name} ist an einen abgetrennten Netzteil angebunden.`,
      focus: { kind: outside.kind, id: outside.id },
      name,
    })
  }

  return (
    <section aria-label="Prüfung des Wegenetzes" className="path-issues">
      <h2>Prüfung des Wegenetzes</h2>
      {items.length === 0 ? (
        <p>Wegenetz vollständig.</p>
      ) : (
        <ul>
          {items.map((item) => (
            <li key={item.key}>
              {item.text}
              {item.focus && (
                <>
                  {' '}
                  <button type="button" onClick={() => onFocus(item.focus!)}>{`${item.name} anzeigen`}</button>
                </>
              )}
            </li>
          ))}
        </ul>
      )}
      {issues.componentCount > 1 && (
        <label>
          <input type="checkbox" checked={highlightParts} onChange={(event) => onHighlightParts(event.target.checked)} />
          Netzteile auf der Karte hervorheben
        </label>
      )}
    </section>
  )
}

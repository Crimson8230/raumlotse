import type { MapSummary } from '../../types/map'

interface Props {
  maps: MapSummary[]
  selectedId: string | undefined
  onSelect: (mapId: string) => void
}

export function MapSelector({ maps, selectedId, onSelect }: Props) {
  if (maps.length === 0) {
    return <p className="map-empty">Noch keine Karte vorhanden.</p>
  }
  return (
    <label className="map-selector">
      Karte
      <select value={selectedId ?? ''} onChange={(event) => onSelect(event.target.value)}>
        {maps.map((map) => (
          <option key={map.id} value={map.id}>
            {map.name}
          </option>
        ))}
      </select>
    </label>
  )
}

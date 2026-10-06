import { Fragment, useState } from 'react'
import type { Connection, MapSummary } from '../../types/map'
import { CONNECTION_TYPE_LABEL } from './connectionLabels'
import './ConnectionLayer.css'

interface Props {
  connections: Connection[]
  currentMapId: string
  maps: MapSummary[]
  onNavigate: (mapId: string) => void
}

/** Markers for stairs/elevators on the current map; rendered inside the map plane's coordinate space. */
export function ConnectionLayer({ connections, currentMapId, maps, onNavigate }: Props) {
  const [openId, setOpenId] = useState<string>()
  const nameOf = (mapId: string) => maps.find((candidate) => candidate.id === mapId)?.name ?? mapId

  return (
    <>
      {connections.map((connection) => {
        const here = connection.points.find((point) => point.mapId === currentMapId)
        if (!here) return null
        const typeLabel = CONNECTION_TYPE_LABEL[connection.type]
        const others = connection.points.filter((point) => point.mapId !== currentMapId)
        const position = { left: `${here.x * 100}%`, top: `${here.y * 100}%` }
        return (
          <Fragment key={connection.id}>
            <button
              type="button"
              className={`connection-marker connection-marker--${connection.type.toLowerCase()}`}
              style={position}
              aria-label={`${connection.name} (${typeLabel})`}
              aria-expanded={openId === connection.id}
              onClick={(event) => {
                event.stopPropagation()
                setOpenId(openId === connection.id ? undefined : connection.id)
              }}
            >
              <span aria-hidden="true">{connection.type === 'ELEVATOR' ? '▲▼' : '⇅'}</span>
              <span className="connection-marker-label">{connection.name}</span>
            </button>
            {openId === connection.id && (
              <div className="connection-popover" style={position} role="group" aria-label={`${connection.name}: Ziele`}>
                {connection.incomplete && <p>Unvollständig: erreicht noch keine zweite Karte.</p>}
                {others.length > 0 && <p>Führt zu:</p>}
                <ul>
                  {others.map((point) => (
                    <li key={point.mapId}>
                      <button type="button" onClick={() => onNavigate(point.mapId)}>
                        {nameOf(point.mapId)}
                      </button>
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </Fragment>
        )
      })}
    </>
  )
}

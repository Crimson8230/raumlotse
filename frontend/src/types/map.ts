import type { EntityStatus } from './room'

export type ConnectionType = 'STAIRS' | 'ELEVATOR'

/** Fractions (0..1) of the map image width (x) and height (y). */
export interface Position {
  x: number
  y: number
}

export interface RoomRef {
  id: string
  name: string
  status: EntityStatus
}

export interface MapSummary {
  id: string
  floorId: string
  /** Building name + floor name. */
  name: string
  widthPx: number
  heightPx: number
  imageVersion: number
  placedRoomCount: number
  /** Only present in the response to an image replacement. */
  aspectRatioChanged?: boolean
}

export interface Placement extends Position {
  room: RoomRef
}

export interface ConnectionPoint extends Position {
  mapId: string
}

export interface Connection {
  id: string
  name: string
  type: ConnectionType
  /** True while fewer than two points exist. */
  incomplete: boolean
  points: ConnectionPoint[]
}

export interface MapDetail extends Omit<MapSummary, 'aspectRatioChanged'> {
  placements: Placement[]
  connections: Connection[]
}

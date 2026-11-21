export type NeighborDirection = 'LEFT' | 'RIGHT' | 'STRAIGHT' | 'UP' | 'DOWN'
export type NeighborKind = 'ROOM' | 'STAIRS' | 'ELEVATOR'
export type NeighborStatus = 'AVAILABLE' | 'RESERVED' | 'OCCUPIED'
export type NeighborsReason = 'ROOM_NOT_ATTACHED' | 'SCALE_MISSING' | 'NO_ORIENTATION' | 'NO_NEIGHBORS'

export interface NeighborEntry {
  direction: NeighborDirection
  kind: NeighborKind
  roomId: string | null
  connectionId: string | null
  name: string
  targetFloorName: string | null
  distanceMeters: number
  walkingSeconds: number
  status: NeighborStatus | null
}

export interface Neighbors {
  roomId: string
  available: boolean
  reason: NeighborsReason | null
  entries: NeighborEntry[]
}

export interface RouteFloorChange {
  connectionId: string
  connectionName: string
  type: 'STAIRS' | 'ELEVATOR'
  fromFloorName: string
  toFloorName: string
}

export interface RouteStep {
  mapId: string
  floorName: string
  waypointId: string
  x: number
  y: number
  floorChange: RouteFloorChange | null
}

export interface Route {
  fromRoomId: string
  toRoomId: string
  accessibleOnly: boolean
  distanceMeters: number
  walkingSeconds: number
  steps: RouteStep[]
}

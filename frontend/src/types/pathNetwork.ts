import type { Position } from './map'

export interface Waypoint extends Position {
  id: string
  mapId: string
}

export interface Segment {
  id: string
  mapId: string
  fromWaypointId: string
  toWaypointId: string
  /** Null while the map has no scale. */
  lengthMeters: number | null
}

export interface Scale {
  /** False while the map has no reference segment: no meter values exist. */
  available: boolean
  pixelsPerMeter: number | null
  widthPx: number
  heightPx: number
}

export interface ReferenceSegmentInput {
  x1: number
  y1: number
  x2: number
  y2: number
  lengthMeters: number
}

export interface ReferenceSegment extends ReferenceSegmentInput {
  mapId: string
  pixelsPerMeter: number
}

export interface RoomAttachment {
  roomId: string
  mapId: string
  waypointId: string
}

export interface ConnectionPointAttachment {
  connectionPointId: string
  mapId: string
  waypointId: string
}

export interface OutsideAttachment {
  kind: 'ROOM' | 'CONNECTION_POINT'
  id: string
}

export interface PathIssues {
  missingScale: boolean
  /** Number of separate network parts (0 for an empty network). */
  componentCount: number
  /** Waypoint ids per part, largest part first. */
  components: string[][]
  unattachedRoomIds: string[]
  unattachedConnectionPointIds: string[]
  attachmentsOutsideMainComponent: OutsideAttachment[]
}

export interface MapNetwork {
  mapId: string
  scale: Scale
  referenceSegment: ReferenceSegment | null
  waypoints: Waypoint[]
  segments: Segment[]
  roomAttachments: RoomAttachment[]
  connectionPointAttachments: ConnectionPointAttachment[]
  issues: PathIssues
}

export interface ConnectionGroup {
  connectionId: string
  name: string
  type: 'STAIRS' | 'ELEVATOR'
  fullyAttached: boolean
  points: { connectionPointId: string; mapId: string; waypointId: string | null }[]
}

export interface NetworkWide {
  maps: MapNetwork[]
  connectionGroups: ConnectionGroup[]
}

export interface SplitResult {
  waypoint: Waypoint
  segments: Segment[]
}

export type PathTool = 'view' | 'add' | 'connect' | 'move' | 'split' | 'delete' | 'reference' | 'attach'

/** A room or connection point that is waiting to be attached to a waypoint. */
export interface AttachTarget {
  kind: 'ROOM' | 'CONNECTION_POINT'
  id: string
}

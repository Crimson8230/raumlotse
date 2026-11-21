import { apiRequest } from './client'
import type { Position } from '../types/map'
import type {
  ConnectionPointAttachment,
  MapNetwork,
  NetworkWide,
  ReferenceSegment,
  ReferenceSegmentInput,
  RoomAttachment,
  Segment,
  SplitResult,
  Waypoint,
} from '../types/pathNetwork'

const json = (body: unknown) => JSON.stringify(body)

/** All maps' networks plus the connection groups across maps. */
export function getNetworkWide(): Promise<NetworkWide> {
  return apiRequest<NetworkWide>('/api/path-network')
}

export function getMapNetwork(mapId: string): Promise<MapNetwork> {
  return apiRequest<MapNetwork>(`/api/maps/${mapId}/path-network`)
}

export function addWaypoint(mapId: string, position: Position): Promise<Waypoint> {
  return apiRequest<Waypoint>(`/api/maps/${mapId}/waypoints`, { method: 'POST', body: json(position) })
}

export function moveWaypoint(mapId: string, waypointId: string, position: Position): Promise<Waypoint> {
  return apiRequest<Waypoint>(`/api/maps/${mapId}/waypoints/${waypointId}`, { method: 'PUT', body: json(position) })
}

export function deleteWaypoint(mapId: string, waypointId: string): Promise<void> {
  return apiRequest<void>(`/api/maps/${mapId}/waypoints/${waypointId}`, { method: 'DELETE' })
}

export function addSegment(mapId: string, fromWaypointId: string, toWaypointId: string): Promise<Segment> {
  return apiRequest<Segment>(`/api/maps/${mapId}/segments`, {
    method: 'POST',
    body: json({ fromWaypointId, toWaypointId }),
  })
}

export function deleteSegment(mapId: string, segmentId: string): Promise<void> {
  return apiRequest<void>(`/api/maps/${mapId}/segments/${segmentId}`, { method: 'DELETE' })
}

/** Inserts a waypoint on the segment; the server projects the position onto it. */
export function splitSegment(mapId: string, segmentId: string, position: Position): Promise<SplitResult> {
  return apiRequest<SplitResult>(`/api/maps/${mapId}/segments/${segmentId}/split`, {
    method: 'POST',
    body: json(position),
  })
}

export function putReferenceSegment(mapId: string, input: ReferenceSegmentInput): Promise<ReferenceSegment> {
  return apiRequest<ReferenceSegment>(`/api/maps/${mapId}/reference-segment`, { method: 'PUT', body: json(input) })
}

export function deleteReferenceSegment(mapId: string): Promise<void> {
  return apiRequest<void>(`/api/maps/${mapId}/reference-segment`, { method: 'DELETE' })
}

export function attachRoom(mapId: string, roomId: string, waypointId: string): Promise<RoomAttachment> {
  return apiRequest<RoomAttachment>(`/api/maps/${mapId}/room-attachments/${roomId}`, {
    method: 'PUT',
    body: json({ waypointId }),
  })
}

export function detachRoom(mapId: string, roomId: string): Promise<void> {
  return apiRequest<void>(`/api/maps/${mapId}/room-attachments/${roomId}`, { method: 'DELETE' })
}

export function attachConnectionPoint(
  mapId: string,
  connectionPointId: string,
  waypointId: string,
): Promise<ConnectionPointAttachment> {
  return apiRequest<ConnectionPointAttachment>(`/api/maps/${mapId}/connection-point-attachments/${connectionPointId}`, {
    method: 'PUT',
    body: json({ waypointId }),
  })
}

export function detachConnectionPoint(mapId: string, connectionPointId: string): Promise<void> {
  return apiRequest<void>(`/api/maps/${mapId}/connection-point-attachments/${connectionPointId}`, {
    method: 'DELETE',
  })
}

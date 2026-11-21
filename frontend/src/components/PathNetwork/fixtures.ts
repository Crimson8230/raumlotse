import type { MapNetwork } from '../../types/pathNetwork'

/** A small corridor w1 - w2 - w3 on a 200 x 100 px map with a 10 px/m scale. */
export function sampleNetwork(overrides: Partial<MapNetwork> = {}): MapNetwork {
  return {
    mapId: 'm1',
    scale: { available: true, pixelsPerMeter: 10, widthPx: 200, heightPx: 100 },
    referenceSegment: { mapId: 'm1', x1: 0.1, y1: 0.9, x2: 0.6, y2: 0.9, lengthMeters: 10, pixelsPerMeter: 10 },
    waypoints: [
      { id: 'w1', mapId: 'm1', x: 0.1, y: 0.5 },
      { id: 'w2', mapId: 'm1', x: 0.5, y: 0.5 },
      { id: 'w3', mapId: 'm1', x: 0.9, y: 0.5 },
    ],
    segments: [
      { id: 's1', mapId: 'm1', fromWaypointId: 'w1', toWaypointId: 'w2', lengthMeters: 8 },
      { id: 's2', mapId: 'm1', fromWaypointId: 'w2', toWaypointId: 'w3', lengthMeters: 12.5 },
    ],
    roomAttachments: [],
    connectionPointAttachments: [],
    issues: {
      missingScale: false,
      componentCount: 1,
      components: [['w1', 'w2', 'w3']],
      unattachedRoomIds: [],
      unattachedConnectionPointIds: [],
      attachmentsOutsideMainComponent: [],
    },
    ...overrides,
  }
}

import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiRequest } from './client'
import {
  addSegment,
  addWaypoint,
  attachConnectionPoint,
  attachRoom,
  deleteReferenceSegment,
  deleteSegment,
  deleteWaypoint,
  detachConnectionPoint,
  detachRoom,
  getMapNetwork,
  getNetworkWide,
  moveWaypoint,
  putReferenceSegment,
  splitSegment,
} from './pathNetwork'

vi.mock('./client', () => ({ apiRequest: vi.fn() }))
const request = vi.mocked(apiRequest)

beforeEach(() => vi.resetAllMocks())

describe('path network API', () => {
  it('reads one map and the whole network through the shared client', async () => {
    request.mockResolvedValue({})
    await getMapNetwork('m1')
    expect(request).toHaveBeenCalledWith('/api/maps/m1/path-network')
    await getNetworkWide()
    expect(request).toHaveBeenCalledWith('/api/path-network')
  })

  it('creates, moves and deletes waypoints', async () => {
    request.mockResolvedValue({})
    await addWaypoint('m1', { x: 0.1, y: 0.2 })
    expect(request).toHaveBeenLastCalledWith('/api/maps/m1/waypoints', { method: 'POST', body: '{"x":0.1,"y":0.2}' })
    await moveWaypoint('m1', 'w1', { x: 0.3, y: 0.4 })
    expect(request).toHaveBeenLastCalledWith('/api/maps/m1/waypoints/w1', { method: 'PUT', body: '{"x":0.3,"y":0.4}' })
    await deleteWaypoint('m1', 'w1')
    expect(request).toHaveBeenLastCalledWith('/api/maps/m1/waypoints/w1', { method: 'DELETE' })
  })

  it('creates, splits and deletes segments', async () => {
    request.mockResolvedValue({})
    await addSegment('m1', 'a', 'b')
    expect(request).toHaveBeenLastCalledWith('/api/maps/m1/segments', {
      method: 'POST',
      body: '{"fromWaypointId":"a","toWaypointId":"b"}',
    })
    await splitSegment('m1', 's1', { x: 0.5, y: 0.5 })
    expect(request).toHaveBeenLastCalledWith('/api/maps/m1/segments/s1/split', {
      method: 'POST',
      body: '{"x":0.5,"y":0.5}',
    })
    await deleteSegment('m1', 's1')
    expect(request).toHaveBeenLastCalledWith('/api/maps/m1/segments/s1', { method: 'DELETE' })
  })

  it('sets and removes the reference segment', async () => {
    request.mockResolvedValue({})
    await putReferenceSegment('m1', { x1: 0.1, y1: 0.5, x2: 0.6, y2: 0.5, lengthMeters: 10 })
    const [path, init] = request.mock.calls[0]
    expect(path).toBe('/api/maps/m1/reference-segment')
    expect(init?.method).toBe('PUT')
    expect(JSON.parse(init?.body as string)).toEqual({ x1: 0.1, y1: 0.5, x2: 0.6, y2: 0.5, lengthMeters: 10 })
    await deleteReferenceSegment('m1')
    expect(request).toHaveBeenLastCalledWith('/api/maps/m1/reference-segment', { method: 'DELETE' })
  })

  it('attaches and detaches rooms and connection points', async () => {
    request.mockResolvedValue({})
    await attachRoom('m1', 'r1', 'w1')
    expect(request).toHaveBeenLastCalledWith('/api/maps/m1/room-attachments/r1', {
      method: 'PUT',
      body: '{"waypointId":"w1"}',
    })
    await detachRoom('m1', 'r1')
    expect(request).toHaveBeenLastCalledWith('/api/maps/m1/room-attachments/r1', { method: 'DELETE' })
    await attachConnectionPoint('m1', 'p1', 'w1')
    expect(request).toHaveBeenLastCalledWith('/api/maps/m1/connection-point-attachments/p1', {
      method: 'PUT',
      body: '{"waypointId":"w1"}',
    })
    await detachConnectionPoint('m1', 'p1')
    expect(request).toHaveBeenLastCalledWith('/api/maps/m1/connection-point-attachments/p1', { method: 'DELETE' })
  })
})

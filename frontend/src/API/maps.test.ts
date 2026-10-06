import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiRequest } from './client'
import {
  createConnection,
  deleteConnection,
  deleteConnectionPoint,
  deleteMap,
  getMap,
  listConnections,
  listMaps,
  mapImageUrl,
  placeRoom,
  putConnectionPoint,
  removePlacement,
  updateConnection,
  uploadMapImage,
} from './maps'

vi.mock('./client', () => ({ apiRequest: vi.fn() }))
const request = vi.mocked(apiRequest)

beforeEach(() => vi.resetAllMocks())

describe('maps API', () => {
  it('lists and loads maps through the shared client', async () => {
    request.mockResolvedValue([])
    await listMaps()
    expect(request).toHaveBeenCalledWith('/api/maps')
    await getMap('m1')
    expect(request).toHaveBeenCalledWith('/api/maps/m1')
  })

  it('uploads the image as multipart form data to the floor map endpoint', async () => {
    request.mockResolvedValue({ id: 'm1' })
    const file = new File([new Uint8Array([1, 2, 3])], 'plan.png', { type: 'image/png' })
    await uploadMapImage('f1', file)
    const [path, init] = request.mock.calls[0]
    expect(path).toBe('/api/floors/f1/map')
    expect(init?.method).toBe('PUT')
    expect(init?.body).toBeInstanceOf(FormData)
    expect((init?.body as FormData).get('image')).toBeInstanceOf(File)
  })

  it('deletes a map', async () => {
    request.mockResolvedValue(undefined)
    await deleteMap('m1')
    expect(request).toHaveBeenCalledWith('/api/maps/m1', { method: 'DELETE' })
  })

  it('builds a cache-busting image url from the image version', () => {
    expect(mapImageUrl({ id: 'm1', imageVersion: 4 })).toBe('/api/maps/m1/image?v=4')
  })

  it('places and removes a room placement', async () => {
    request.mockResolvedValue({})
    await placeRoom('m1', 'r1', { x: 0.25, y: 0.5 })
    expect(request).toHaveBeenCalledWith('/api/maps/m1/placements/r1', {
      method: 'PUT',
      body: '{"x":0.25,"y":0.5}',
    })
    await removePlacement('m1', 'r1')
    expect(request).toHaveBeenCalledWith('/api/maps/m1/placements/r1', { method: 'DELETE' })
  })
})

describe('connections API', () => {
  beforeEach(() => vi.resetAllMocks())

  it('creates, updates, deletes and lists connections', async () => {
    request.mockResolvedValue({})
    await listConnections()
    expect(request).toHaveBeenCalledWith('/api/connections')
    await createConnection({ name: 'Aufzug A', type: 'ELEVATOR' })
    expect(request).toHaveBeenCalledWith('/api/connections', {
      method: 'POST',
      body: '{"name":"Aufzug A","type":"ELEVATOR"}',
    })
    await updateConnection('c1', { name: 'B', type: 'STAIRS' })
    expect(request).toHaveBeenCalledWith('/api/connections/c1', { method: 'PUT', body: '{"name":"B","type":"STAIRS"}' })
    await deleteConnection('c1')
    expect(request).toHaveBeenCalledWith('/api/connections/c1', { method: 'DELETE' })
  })

  it('puts and removes the point of a connection on a map', async () => {
    request.mockResolvedValue({})
    await putConnectionPoint('c1', 'm1', { x: 0.5, y: 0.25 })
    expect(request).toHaveBeenCalledWith('/api/connections/c1/points/m1', { method: 'PUT', body: '{"x":0.5,"y":0.25}' })
    await deleteConnectionPoint('c1', 'm1')
    expect(request).toHaveBeenCalledWith('/api/connections/c1/points/m1', { method: 'DELETE' })
  })
})

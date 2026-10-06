import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MapUploadControl } from './MapUploadControl'
import * as mapsApi from '../../API/maps'
import * as buildingsApi from '../../API/buildings'
import * as floorsApi from '../../API/floors'
import { ApiError } from '../../API/client'
import type { MapSummary } from '../../types/map'

vi.mock('../../API/maps')
vi.mock('../../API/buildings')
vi.mock('../../API/floors')

const maps = vi.mocked(mapsApi)
const buildings = vi.mocked(buildingsApi)
const floors = vi.mocked(floorsApi)

const existing: MapSummary = {
  id: 'm1', floorId: 'f1', name: 'Haus A – EG', widthPx: 100, heightPx: 50, imageVersion: 1, placedRoomCount: 2,
}
const png = () => new File([new Uint8Array([1])], 'plan.png', { type: 'image/png' })

beforeEach(() => {
  vi.resetAllMocks()
  buildings.listBuildings.mockResolvedValue([{ id: 'b1', name: 'Haus A', status: 'ACTIVE', hasElevator: false }])
  floors.listFloors.mockResolvedValue([
    { id: 'f1', buildingId: 'b1', name: 'EG', status: 'ACTIVE', groundFloor: true },
    { id: 'f2', buildingId: 'b1', name: '1. OG', status: 'ACTIVE', groundFloor: false },
  ])
})

describe('MapUploadControl (create)', () => {
  it('offers only floors without a map and uploads the chosen file', async () => {
    const user = userEvent.setup()
    const onSaved = vi.fn()
    maps.uploadMapImage.mockResolvedValue({ ...existing, id: 'm2', floorId: 'f2' })
    render(<MapUploadControl existingMaps={[existing]} onSaved={onSaved} />)

    await user.selectOptions(await screen.findByLabelText(/gebäude/i), 'b1')
    expect(await screen.findByRole('option', { name: '1. OG' })).toBeInTheDocument()
    expect(screen.queryByRole('option', { name: 'EG' })).not.toBeInTheDocument()

    await user.selectOptions(screen.getByLabelText(/stockwerk/i), 'f2')
    await user.upload(screen.getByLabelText(/grundriss/i), png())
    await user.click(screen.getByRole('button', { name: /karte anlegen/i }))

    await waitFor(() => expect(maps.uploadMapImage).toHaveBeenCalledWith('f2', expect.any(File)))
    expect(onSaved).toHaveBeenCalledWith(expect.objectContaining({ id: 'm2' }))
  })

  it('shows the server rejection message and does not report success', async () => {
    const user = userEvent.setup()
    const onSaved = vi.fn()
    maps.uploadMapImage.mockRejectedValue(
      new ApiError(415, { title: 'Unsupported', status: 415, detail: 'Only PNG and JPEG images are supported.' }),
    )
    render(<MapUploadControl existingMaps={[]} onSaved={onSaved} />)

    await user.selectOptions(await screen.findByLabelText(/gebäude/i), 'b1')
    await user.selectOptions(await screen.findByLabelText(/stockwerk/i), 'f1')
    await user.upload(screen.getByLabelText(/grundriss/i), png())
    await user.click(screen.getByRole('button', { name: /karte anlegen/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Only PNG and JPEG images are supported.')
    expect(onSaved).not.toHaveBeenCalled()
  })
})

describe('MapUploadControl (replace)', () => {
  it('replaces the image of the given map and warns when the aspect ratio changed', async () => {
    const user = userEvent.setup()
    maps.uploadMapImage.mockResolvedValue({ ...existing, aspectRatioChanged: true, imageVersion: 2 })
    render(<MapUploadControl map={existing} existingMaps={[existing]} onSaved={vi.fn()} />)

    expect(screen.queryByLabelText(/gebäude/i)).not.toBeInTheDocument()
    await user.upload(screen.getByLabelText(/grundriss/i), png())
    await user.click(screen.getByRole('button', { name: /bild ersetzen/i }))

    await waitFor(() => expect(maps.uploadMapImage).toHaveBeenCalledWith('f1', expect.any(File)))
    expect(await screen.findByRole('status')).toHaveTextContent(/seitenverhältnis/i)
  })
})

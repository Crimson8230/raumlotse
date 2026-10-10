import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../API/client'
import * as api from '../../API/roomDevices'
import { RoomDeviceControls } from './RoomDeviceControls'

vi.mock('../../API/roomDevices')
const deviceApi = vi.mocked(api)

const lighting = { kind: 'LIGHTING' as const, enabled: true, state: false, updatedAt: '2026-10-01T10:00:00Z' }
const ventilation = { kind: 'VENTILATION' as const, enabled: true, state: true, updatedAt: '2026-10-01T10:00:00Z' }

beforeEach(() => {
  vi.resetAllMocks()
  deviceApi.getRoomDeviceControls.mockResolvedValue({ roomId: 'r1', reservationId: 'b1', devices: [lighting, ventilation] })
})

describe('RoomDeviceControls', () => {
  it('renders confirmed device state and updates after an acknowledged command', async () => {
    const user = userEvent.setup()
    deviceApi.setRoomDeviceState.mockResolvedValue({ ...lighting, state: true })
    render(<RoomDeviceControls roomId="r1" />)
    expect(await screen.findByRole('heading', { name: 'Beleuchtung' })).toBeInTheDocument()
    expect(screen.getByText('Aus')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /beleuchtung einschalten/i }))
    await waitFor(() => expect(screen.getAllByText('An')).toHaveLength(2))
  })

  it('shows the projector explanation only when the API omits it', async () => {
    render(<RoomDeviceControls roomId="r1" />)
    expect(await screen.findByText(/kein beamer eingerichtet/i)).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Projector' })).not.toBeInTheDocument()
  })

  it('renders a configured projector returned by the API', async () => {
    deviceApi.getRoomDeviceControls.mockResolvedValue({ roomId: 'r1', reservationId: 'b1', devices: [{ kind: 'PROJECTOR', enabled: true, state: false, updatedAt: '2026-10-01T10:00:00Z' }] })
    render(<RoomDeviceControls roomId="r1" />)
    expect(await screen.findByRole('heading', { name: 'Projector' })).toBeInTheDocument()
    expect(screen.queryByText(/kein beamer eingerichtet/i)).not.toBeInTheDocument()
  })

  it('shows a safe forbidden state and does not retry commands', async () => {
    deviceApi.getRoomDeviceControls.mockRejectedValue(new ApiError(403, { title: 'Forbidden', status: 403, detail: 'denied' }))
    render(<RoomDeviceControls roomId="r1" />)
    expect(await screen.findByRole('alert')).toHaveTextContent(/nur während ihrer aktiven reservierung/i)
    expect(deviceApi.setRoomDeviceState).not.toHaveBeenCalled()
  })

  it('labels the door with its lock state', async () => {
    const door = { kind: 'DOOR' as const, enabled: true, state: false, updatedAt: '2026-10-01T10:00:00Z' }
    deviceApi.getRoomDeviceControls.mockResolvedValue({ roomId: 'r1', reservationId: 'b1', devices: [door] })
    deviceApi.setRoomDeviceState.mockResolvedValue({ ...door, state: true })
    const user = userEvent.setup()
    render(<RoomDeviceControls roomId="r1" />)

    expect(await screen.findByRole('heading', { name: 'Tür' })).toBeInTheDocument()
    expect(screen.getByText('Verriegelt')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Tür entriegeln' }))
    expect(await screen.findByText('Entriegelt')).toBeInTheDocument()
    expect(deviceApi.setRoomDeviceState).toHaveBeenCalledWith('r1', 'DOOR', { state: true })
  })
})

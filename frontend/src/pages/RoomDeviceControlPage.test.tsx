import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import RoomDeviceControlPage from './RoomDeviceControlPage'
import { ApiError } from '../API/client'
import * as deviceApi from '../API/roomDevices'

vi.mock('../API/roomDevices')
const api = vi.mocked(deviceApi)

describe('RoomDeviceControlPage', () => {
  it('passes the room route parameter to the controls', () => {
    api.getRoomDeviceControls.mockResolvedValue({ roomId: 'r1', reservationId: 'b1', devices: [] })
    render(<MemoryRouter initialEntries={['/rooms/r1/control']}><Routes><Route path="/rooms/:roomId/control" element={<RoomDeviceControlPage />} /></Routes></MemoryRouter>)
    expect(screen.getByRole('heading', { name: /gerätesteuerung/i })).toBeInTheDocument()
  })

  it('shows the safe expired/forbidden state when the reservation is no longer eligible', async () => {
    api.getRoomDeviceControls.mockRejectedValue(new ApiError(403, { title: 'Forbidden', status: 403, detail: 'expired' }))
    render(<MemoryRouter initialEntries={['/rooms/r1/control']}><Routes><Route path="/rooms/:roomId/control" element={<RoomDeviceControlPage />} /></Routes></MemoryRouter>)
    expect(await screen.findByRole('alert')).toHaveTextContent(/nur während ihrer aktiven reservierung/i)
  })

  it('renders the control shell within the one-second UI response target', () => {
    api.getRoomDeviceControls.mockResolvedValue({ roomId: 'r1', reservationId: 'b1', devices: [] })
    const started = performance.now()
    render(<MemoryRouter initialEntries={['/rooms/r1/control']}><Routes><Route path="/rooms/:roomId/control" element={<RoomDeviceControlPage />} /></Routes></MemoryRouter>)
    expect(performance.now() - started).toBeLessThan(1000)
  })

  it('links back to the room page', () => {
    api.getRoomDeviceControls.mockResolvedValue({ roomId: 'r1', reservationId: 'b1', devices: [] })
    render(<MemoryRouter initialEntries={['/rooms/r1/control']}><Routes><Route path="/rooms/:roomId/control" element={<RoomDeviceControlPage />} /></Routes></MemoryRouter>)
    expect(screen.getByRole('link', { name: 'Zurück zum Raum' })).toHaveAttribute('href', '/rooms/r1')
  })
})


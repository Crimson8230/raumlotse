import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import CheckInPage from './CheckInPage'
import { ApiError } from '../API/client'
import * as checkInApi from '../API/checkIn'
import type { CheckInPreview } from '../types/checkIn'
import { formatTime } from '../utils/date'

vi.mock('../API/checkIn')
const api = vi.mocked(checkInApi)

const booking = {
  id: 'b1',
  startTime: '2026-10-09T08:00:00Z',
  endTime: '2026-10-09T09:00:00Z',
  reservedFor: 'Projektgruppe',
}

function preview(overrides: Partial<CheckInPreview>): CheckInPreview {
  return { roomId: 'r1', roomName: 'Seminarraum 1', outcome: 'READY', reservation: booking, checkInOpensAt: null, ...overrides }
}

function renderAt(url: string) {
  return render(
    <MemoryRouter initialEntries={[url]}>
      <Routes><Route path="/rooms/:roomId/check-in" element={<CheckInPage />} /></Routes>
    </MemoryRouter>,
  )
}

describe('CheckInPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    api.checkIn.mockResolvedValue({
      reservationId: 'b1', status: 'ACTIVE', alreadyActive: false, checkInMethod: 'QR',
      checkedInAt: '2026-10-09T08:01:00Z', devices: null, failedDevices: [],
    })
  })

  it('shows the matching booking and confirms it only after the button is pressed', async () => {
    api.getCheckInPreview.mockResolvedValue(preview({}))
    const user = userEvent.setup()
    renderAt('/rooms/r1/check-in?method=qr')

    expect(await screen.findByRole('heading', { name: /seminarraum 1/i })).toBeInTheDocument()
    expect(screen.getByText(/projektgruppe/i)).toBeInTheDocument()
    const start = formatTime(new Date(booking.startTime))
    const end = formatTime(new Date(booking.endTime))
    expect(screen.getByText(new RegExp(`${start}\\s*–\\s*${end}`))).toBeInTheDocument()
    expect(api.checkIn).not.toHaveBeenCalled()

    await user.click(screen.getByRole('button', { name: 'Anwesenheit bestätigen' }))

    expect(api.checkIn).toHaveBeenCalledWith('r1', 'QR')
    expect(await screen.findByRole('status')).toHaveTextContent('Anwesenheit bestätigt')
  })

  it('sends NFC when opened through the NFC link', async () => {
    api.getCheckInPreview.mockResolvedValue(preview({}))
    const user = userEvent.setup()
    renderAt('/rooms/r1/check-in?method=nfc')
    await user.click(await screen.findByRole('button', { name: 'Anwesenheit bestätigen' }))
    expect(api.checkIn).toHaveBeenCalledWith('r1', 'NFC')
  })

  it('defaults to QR for a missing or unknown method', async () => {
    api.getCheckInPreview.mockResolvedValue(preview({}))
    const user = userEvent.setup()
    renderAt('/rooms/r1/check-in?method=bluetooth')
    await user.click(await screen.findByRole('button', { name: 'Anwesenheit bestätigen' }))
    expect(api.checkIn).toHaveBeenCalledWith('r1', 'QR')
  })

  it('tells the user when check-in opens', async () => {
    api.getCheckInPreview.mockResolvedValue(preview({ outcome: 'TOO_EARLY', checkInOpensAt: booking.startTime }))
    renderAt('/rooms/r1/check-in')
    expect(await screen.findByText(`Check-in ab ${formatTime(new Date(booking.startTime))} möglich`)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Anwesenheit bestätigen' })).not.toBeInTheDocument()
  })

  it('explains that the room is still occupied', async () => {
    api.getCheckInPreview.mockResolvedValue(preview({
      outcome: 'TOO_EARLY', checkInOpensAt: booking.startTime,
      detail: 'Der Raum ist noch belegt. Der Check-in ist ab 10:00 Uhr möglich.',
    }))
    renderAt('/rooms/r1/check-in')
    expect(await screen.findByText('Der Raum ist noch belegt. Der Check-in ist ab 10:00 Uhr möglich.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Anwesenheit bestätigen' })).not.toBeInTheDocument()
  })

  it.each([
    ['EXPIRED', /abgelaufen/i],
    ['NO_MATCH', /keine passende buchung/i],
    ['ALREADY_ACTIVE', /bereits in nutzung/i],
  ] as const)('explains the %s outcome without a confirm button', async (outcome, text) => {
    api.getCheckInPreview.mockResolvedValue(preview({ outcome, reservation: outcome === 'NO_MATCH' ? null : booking }))
    renderAt('/rooms/r1/check-in')
    expect(await screen.findByText(text)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Anwesenheit bestätigen' })).not.toBeInTheDocument()
  })

  it('shows the server explanation when the check-in is rejected', async () => {
    api.getCheckInPreview.mockResolvedValue(preview({}))
    api.checkIn.mockRejectedValue(new ApiError(409, { title: 'Check-in nicht möglich', status: 409, detail: 'Die Buchung ist abgelaufen.', code: 'EXPIRED' }))
    const user = userEvent.setup()
    renderAt('/rooms/r1/check-in')
    await user.click(await screen.findByRole('button', { name: 'Anwesenheit bestätigen' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Die Buchung ist abgelaufen.')
  })

  it('shows an error when the room cannot be loaded', async () => {
    api.getCheckInPreview.mockRejectedValue(new ApiError(404, { title: 'Not Found', status: 404, detail: 'Raum nicht gefunden.' }))
    renderAt('/rooms/r1/check-in')
    expect(await screen.findByRole('alert')).toHaveTextContent('Raum nicht gefunden.')
  })

  it('shows the prepared room after confirming', async () => {
    api.getCheckInPreview.mockResolvedValue(preview({}))
    api.checkIn.mockResolvedValue({
      reservationId: 'b1', status: 'ACTIVE', alreadyActive: false, checkInMethod: 'QR', checkedInAt: '2026-10-09T08:01:00Z',
      devices: { lighting: true, ventilation: true, door: 'UNLOCKED' }, failedDevices: [],
    })
    const user = userEvent.setup()
    renderAt('/rooms/r1/check-in')
    await user.click(await screen.findByRole('button', { name: 'Anwesenheit bestätigen' }))

    expect(await screen.findByText('Licht an')).toBeInTheDocument()
    expect(screen.getByText('Lüftung an')).toBeInTheDocument()
    expect(screen.getByText('Tür entriegelt')).toBeInTheDocument()
    expect(screen.queryByText(/konnten nicht geschaltet werden/)).not.toBeInTheDocument()
  })

  it('names devices that could not be switched', async () => {
    api.getCheckInPreview.mockResolvedValue(preview({}))
    api.checkIn.mockResolvedValue({
      reservationId: 'b1', status: 'ACTIVE', alreadyActive: false, checkInMethod: 'QR', checkedInAt: '2026-10-09T08:01:00Z',
      devices: { lighting: true, ventilation: true, door: 'LOCKED' }, failedDevices: ['DOOR'],
    })
    const user = userEvent.setup()
    renderAt('/rooms/r1/check-in')
    await user.click(await screen.findByRole('button', { name: 'Anwesenheit bestätigen' }))

    expect(await screen.findByText('Folgende Geräte konnten nicht geschaltet werden: Tür')).toBeInTheDocument()
  })
})

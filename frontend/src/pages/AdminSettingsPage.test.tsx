import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AdminSettingsPage from './AdminSettingsPage'
import { ApiError } from '../API/client'
import * as settingsApi from '../API/checkInSettings'

vi.mock('../API/checkInSettings')
const api = vi.mocked(settingsApi)

describe('AdminSettingsPage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    api.getCheckInSettings.mockResolvedValue({ earlyCheckInMinutes: 10, gracePeriodMinutes: 5, updatedAt: '2026-10-10T08:00:00Z' })
  })

  it('shows the current check-in times', async () => {
    render(<AdminSettingsPage />)
    expect(await screen.findByLabelText('Früher Check-in (Minuten vor Beginn)')).toHaveValue(10)
    expect(screen.getByLabelText('Kulanzzeit (Minuten nach Beginn)')).toHaveValue(5)
  })

  it('saves changed values and confirms', async () => {
    api.saveCheckInSettings.mockResolvedValue({ earlyCheckInMinutes: 0, gracePeriodMinutes: 15, updatedAt: '2026-10-10T08:05:00Z' })
    const user = userEvent.setup()
    render(<AdminSettingsPage />)
    const early = await screen.findByLabelText('Früher Check-in (Minuten vor Beginn)')
    await user.clear(early)
    await user.type(early, '0')
    const grace = screen.getByLabelText('Kulanzzeit (Minuten nach Beginn)')
    await user.clear(grace)
    await user.type(grace, '15')
    await user.click(screen.getByRole('button', { name: 'Speichern' }))

    expect(api.saveCheckInSettings).toHaveBeenCalledWith({ earlyCheckInMinutes: 0, gracePeriodMinutes: 15 })
    expect(await screen.findByRole('status')).toHaveTextContent('Gespeichert')
  })

  it('rejects values outside the ranges without calling the server', async () => {
    const user = userEvent.setup()
    render(<AdminSettingsPage />)
    const grace = await screen.findByLabelText('Kulanzzeit (Minuten nach Beginn)')
    await user.clear(grace)
    await user.type(grace, '45')
    await user.click(screen.getByRole('button', { name: 'Speichern' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('zwischen 1 und 30 Minuten')
    expect(api.saveCheckInSettings).not.toHaveBeenCalled()
  })

  it('shows server errors', async () => {
    api.saveCheckInSettings.mockRejectedValue(new ApiError(503, { title: 'Unavailable', status: 503, detail: 'Speichern nicht möglich.' }))
    const user = userEvent.setup()
    render(<AdminSettingsPage />)
    await screen.findByLabelText('Früher Check-in (Minuten vor Beginn)')
    await user.click(screen.getByRole('button', { name: 'Speichern' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Speichern nicht möglich.')
  })
})

import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getAdminStatistics } from '../API/adminStatistics'
import AdminStatisticsPage from './AdminStatisticsPage'
import type { AdminStatisticsResponse } from '../types/adminStatistics'

vi.mock('../API/adminStatistics')

const response: AdminStatisticsResponse = {
  period: { from: '2026-01-01', to: '2026-02-01', timezone: 'Europe/Berlin' },
  summary: {
    totalReservations: 4,
    validReservationCount: 3,
    cancelledReservationCount: 1,
    cancellationRatePercent: 25,
    attendeeSum: 30,
    attendeeBookingCount: 3,
    averageExpectedAttendees: 10,
    missingAttendeeCount: 0,
  },
  rooms: [{ roomId: 'r1', roomName: 'Room A', roomStatus: 'ACTIVE', bookingCount: 2, bookedSeconds: 7200, utilizationPercent: 1.61 }],
  features: [{ featureId: 'f1', featureName: 'Projector', featureStatus: 'ACTIVE', bookingCount: 2, bookedSeconds: 7200 }],
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(getAdminStatistics).mockResolvedValue(response)
})

describe('AdminStatisticsPage', () => {
  it('loads the shared snapshot and renders all metric groups', async () => {
    render(<AdminStatisticsPage />)
    expect(screen.getByRole('status')).toHaveTextContent(/geladen/i)
    expect(await screen.findByRole('heading', { name: 'Admin-Statistiken' })).toBeInTheDocument()
    expect(screen.getByText('Room A')).toBeInTheDocument()
    expect(screen.getByText('Projector')).toBeInTheDocument()
    expect(screen.getByText('10')).toBeInTheDocument()
    expect(screen.getByText(/25\s*%/)).toBeInTheDocument()
  })

  it('reloads every metric for the submitted period', async () => {
    const user = userEvent.setup()
    render(<AdminStatisticsPage />)
    await screen.findByRole('heading', { name: 'Admin-Statistiken' })
    await user.clear(screen.getByLabelText('Von'))
    await user.type(screen.getByLabelText('Von'), '2026-02-01')
    await user.clear(screen.getByLabelText('Bis'))
    await user.type(screen.getByLabelText('Bis'), '2026-03-01')
    await user.click(screen.getByRole('button', { name: 'Zeitraum anwenden' }))
    await waitFor(() => expect(getAdminStatistics).toHaveBeenLastCalledWith({ from: '2026-02-01', to: '2026-03-01' }))
  })
})

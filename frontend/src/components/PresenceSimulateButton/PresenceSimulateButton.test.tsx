import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as presenceApi from '../../API/presence'
import { PresenceSimulateButton } from './PresenceSimulateButton'

const mode = vi.hoisted(() => ({ adminMode: false }))
vi.mock('../../auth/useAdminMode', () => ({
  useAdminMode: () => ({ loading: false, failed: false, admin: mode.adminMode, adminMode: mode.adminMode, setMode: vi.fn() }),
}))
vi.mock('../../API/presence')
const api = vi.mocked(presenceApi)

function clock(iso: string): string {
  return new Date(iso).toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

describe('PresenceSimulateButton', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    mode.adminMode = false
  })

  it('is hidden outside administration mode', () => {
    render(<PresenceSimulateButton roomId="r1" lastPresenceAt={null} />)
    expect(screen.queryByRole('button', { name: 'Bewegung simulieren' })).not.toBeInTheDocument()
  })

  it('records simulated motion and shows when presence was last detected', async () => {
    mode.adminMode = true
    api.simulatePresence.mockResolvedValue({ recorded: true, lastPresenceAt: '2026-10-09T08:00:05Z' })
    const user = userEvent.setup()
    render(<PresenceSimulateButton roomId="r1" lastPresenceAt={null} />)

    await user.click(screen.getByRole('button', { name: 'Bewegung simulieren' }))

    expect(api.simulatePresence).toHaveBeenCalledWith('r1')
    expect(await screen.findByText(`Zuletzt Bewegung erkannt: ${clock('2026-10-09T08:00:05Z')}`)).toBeInTheDocument()
  })

  it('says when no booking is in use', async () => {
    mode.adminMode = true
    api.simulatePresence.mockResolvedValue({ recorded: false, lastPresenceAt: null })
    const user = userEvent.setup()
    render(<PresenceSimulateButton roomId="r1" lastPresenceAt={null} />)

    await user.click(screen.getByRole('button', { name: 'Bewegung simulieren' }))

    expect(await screen.findByText('Keine aktive Buchung – Bewegung nicht erfasst')).toBeInTheDocument()
  })

  it('shows a known last presence from the room status', () => {
    mode.adminMode = true
    render(<PresenceSimulateButton roomId="r1" lastPresenceAt="2026-10-09T07:59:00Z" />)
    expect(screen.getByText(`Zuletzt Bewegung erkannt: ${clock('2026-10-09T07:59:00Z')}`)).toBeInTheDocument()
  })
})

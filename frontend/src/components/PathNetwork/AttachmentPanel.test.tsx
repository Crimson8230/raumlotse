import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { AttachmentPanel } from './AttachmentPanel'
import { sampleNetwork } from './fixtures'

const rooms = [
  { id: 'r1', label: 'Raum 1' },
  { id: 'r2', label: 'Raum 2' },
]
const points = [
  { id: 'p1', label: 'Aufzug A' },
  { id: 'p2', label: 'Treppe B' },
]
const network = sampleNetwork({
  roomAttachments: [{ roomId: 'r1', mapId: 'm1', waypointId: 'w1' }],
  connectionPointAttachments: [{ connectionPointId: 'p1', mapId: 'm1', waypointId: 'w2' }],
})

describe('AttachmentPanel', () => {
  it('lists rooms and connection points with their attachment state in words', () => {
    render(<AttachmentPanel network={network} rooms={rooms} connectionPoints={points} canEdit onSelectTarget={vi.fn()} onDetach={vi.fn()} />)
    const room1 = screen.getByText('Raum 1').closest('li')!
    const room2 = screen.getByText('Raum 2').closest('li')!
    expect(within(room1).getByText('angebunden')).toBeInTheDocument()
    expect(within(room2).getByText('nicht angebunden')).toBeInTheDocument()
    expect(within(screen.getByText('Treppe B').closest('li')!).getByText('nicht angebunden')).toBeInTheDocument()
  })

  it('selects a room for attachment so the next click on a waypoint attaches it', async () => {
    const user = userEvent.setup()
    const onSelectTarget = vi.fn()
    render(<AttachmentPanel network={network} rooms={rooms} connectionPoints={points} canEdit onSelectTarget={onSelectTarget} onDetach={vi.fn()} />)
    await user.click(within(screen.getByText('Raum 2').closest('li')!).getByRole('button', { name: /anbinden/i }))
    expect(onSelectTarget).toHaveBeenCalledWith({ kind: 'ROOM', id: 'r2' })
  })

  it('selects a connection point and marks the chosen target as pressed', async () => {
    const user = userEvent.setup()
    const onSelectTarget = vi.fn()
    render(<AttachmentPanel network={network} rooms={rooms} connectionPoints={points} canEdit target={{ kind: 'CONNECTION_POINT', id: 'p2' }} onSelectTarget={onSelectTarget} onDetach={vi.fn()} />)
    const button = within(screen.getByText('Treppe B').closest('li')!).getByRole('button', { name: /anbinden/i })
    expect(button).toHaveAttribute('aria-pressed', 'true')
    await user.click(button)
    expect(onSelectTarget).toHaveBeenCalledWith(undefined)
  })

  it('detaches an attached room or connection point', async () => {
    const user = userEvent.setup()
    const onDetach = vi.fn()
    render(<AttachmentPanel network={network} rooms={rooms} connectionPoints={points} canEdit onSelectTarget={vi.fn()} onDetach={onDetach} />)
    await user.click(within(screen.getByText('Raum 1').closest('li')!).getByRole('button', { name: /lösen/i }))
    expect(onDetach).toHaveBeenCalledWith({ kind: 'ROOM', id: 'r1' })
    await user.click(within(screen.getByText('Aufzug A').closest('li')!).getByRole('button', { name: /lösen/i }))
    expect(onDetach).toHaveBeenCalledWith({ kind: 'CONNECTION_POINT', id: 'p1' })
  })

  it('shows state but no actions to users without edit rights', () => {
    render(<AttachmentPanel network={network} rooms={rooms} connectionPoints={points} canEdit={false} onSelectTarget={vi.fn()} onDetach={vi.fn()} />)
    expect(screen.getByText('Raum 2')).toBeInTheDocument()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })
})

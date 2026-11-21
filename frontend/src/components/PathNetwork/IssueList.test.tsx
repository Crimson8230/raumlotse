import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { IssueList } from './IssueList'
import { sampleNetwork } from './fixtures'

const names = { rooms: { r1: 'Raum 1', r2: 'Raum 2' }, points: { p1: 'Aufzug A' } }

describe('IssueList', () => {
  it('reports a complete network in words', () => {
    render(<IssueList network={sampleNetwork()} names={names} highlightParts={false} onHighlightParts={vi.fn()} onFocus={vi.fn()} />)
    expect(screen.getByText('Wegenetz vollständig.')).toBeInTheDocument()
  })

  it('lists missing scale, separate parts, unattached rooms and points and out-of-main attachments', async () => {
    const user = userEvent.setup()
    const onFocus = vi.fn()
    const onHighlightParts = vi.fn()
    const network = sampleNetwork({
      issues: {
        missingScale: true,
        componentCount: 2,
        components: [['w1', 'w2'], ['w3']],
        unattachedRoomIds: ['r1'],
        unattachedConnectionPointIds: ['p1'],
        attachmentsOutsideMainComponent: [{ kind: 'ROOM', id: 'r2' }],
      },
    })
    render(<IssueList network={network} names={names} highlightParts={false} onHighlightParts={onHighlightParts} onFocus={onFocus} />)

    expect(screen.queryByText('Wegenetz vollständig.')).not.toBeInTheDocument()
    expect(screen.getByText(/maßstab fehlt/i)).toBeInTheDocument()
    expect(screen.getByText(/2 getrennten teilen/i)).toBeInTheDocument()
    expect(screen.getByText(/raum 1 ist nicht angebunden/i)).toBeInTheDocument()
    expect(screen.getByText(/aufzug a ist nicht angebunden/i)).toBeInTheDocument()
    expect(screen.getByText(/raum 2 ist an einen abgetrennten netzteil angebunden/i)).toBeInTheDocument()

    await user.click(screen.getByLabelText(/netzteile auf der karte hervorheben/i))
    expect(onHighlightParts).toHaveBeenCalledWith(true)
    await user.click(screen.getByRole('button', { name: 'Raum 1 anzeigen' }))
    expect(onFocus).toHaveBeenCalledWith({ kind: 'ROOM', id: 'r1' })
  })
})

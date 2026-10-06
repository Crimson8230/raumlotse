import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { UnplacedRoomList } from './UnplacedRoomList'
import type { RoomRef } from '../../types/map'

const rooms: RoomRef[] = [
  { id: 'r1', name: 'Raum 1', status: 'ACTIVE' },
  { id: 'r2', name: 'Raum 2', status: 'ACTIVE' },
]

describe('UnplacedRoomList', () => {
  it('lists every unplaced room', () => {
    render(<UnplacedRoomList rooms={rooms} activeRoomId={undefined} onSelect={vi.fn()} canSelect />)
    expect(screen.getAllByRole('listitem')).toHaveLength(2)
    expect(screen.getByText('Raum 1')).toBeInTheDocument()
  })

  it('reports the selected room and marks it as active', async () => {
    const onSelect = vi.fn()
    const { rerender } = render(<UnplacedRoomList rooms={rooms} activeRoomId={undefined} onSelect={onSelect} canSelect />)

    await userEvent.click(screen.getByRole('button', { name: 'Raum 2' }))
    expect(onSelect).toHaveBeenCalledWith('r2')

    rerender(<UnplacedRoomList rooms={rooms} activeRoomId="r2" onSelect={onSelect} canSelect />)
    expect(screen.getByRole('button', { name: 'Raum 2' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: 'Raum 1' })).toHaveAttribute('aria-pressed', 'false')
  })

  it('shows a message when every room is placed', () => {
    render(<UnplacedRoomList rooms={[]} activeRoomId={undefined} onSelect={vi.fn()} canSelect />)
    expect(screen.getByText(/alle räume sind platziert/i)).toBeInTheDocument()
  })

  it('offers no selection to non-admins', () => {
    render(<UnplacedRoomList rooms={rooms} activeRoomId={undefined} onSelect={vi.fn()} canSelect={false} />)
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
    expect(screen.getByText('Raum 1')).toBeInTheDocument()
  })
})

import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { RoomDeviceIcons } from './RoomDeviceIcons'

describe('RoomDeviceIcons', () => {
  it('shows every device switched on and the door unlocked', () => {
    render(<RoomDeviceIcons devices={{ lighting: true, ventilation: true, door: 'UNLOCKED' }} />)
    const list = screen.getByRole('list', { name: 'Geräte im Raum' })
    expect(list).toHaveTextContent('Licht an')
    expect(list).toHaveTextContent('Lüftung an')
    expect(list).toHaveTextContent('Tür entriegelt')
  })

  it('shows every device off and the door locked, read-only', () => {
    render(<RoomDeviceIcons devices={{ lighting: false, ventilation: false, door: 'LOCKED' }} />)
    expect(screen.getByText('Licht aus')).toBeInTheDocument()
    expect(screen.getByText('Lüftung aus')).toBeInTheDocument()
    expect(screen.getByText('Tür verriegelt')).toBeInTheDocument()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })
})

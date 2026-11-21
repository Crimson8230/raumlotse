import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { PathToolbar } from './PathToolbar'

describe('PathToolbar', () => {
  it('marks the selected tool as pressed and explains what a click does', () => {
    render(<PathToolbar tool="connect" onSelect={vi.fn()} />)
    expect(screen.getByRole('button', { name: 'Verbinden' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: 'Löschen' })).toHaveAttribute('aria-pressed', 'false')
    expect(screen.getByRole('status')).toHaveTextContent(/zwei wegpunkte/i)
  })

  it('selects another tool', async () => {
    const onSelect = vi.fn()
    render(<PathToolbar tool="view" onSelect={onSelect} />)
    await userEvent.click(screen.getByRole('button', { name: 'Teilen' }))
    expect(onSelect).toHaveBeenCalledWith('split')
  })
})

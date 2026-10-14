import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { Chip } from './Chip'

describe('Chip', () => {
  it.each([
    ['frei', 'ui-pattern-empty'],
    ['belegt', 'ui-pattern-hatch'],
    ['meine', 'ui-pattern-solid'],
    ['jetzt', 'ui-pattern-line'],
    ['gesperrt', 'ui-pattern-dotted'],
  ] as const)('shows the word and pattern for %s', (state, pattern) => {
    const { container } = render(<Chip state={state} />)
    expect(screen.getByText(state)).toBeInTheDocument()
    expect(container.querySelector(`.${pattern}`)).toBeInTheDocument()
  })

  it('lets the text override the word', () => {
    render(<Chip state="belegt">Belegt bis 12:00</Chip>)
    expect(screen.getByText('Belegt bis 12:00')).toBeInTheDocument()
  })

  it('renders a neutral chip with text only and without a pattern swatch', () => {
    const { container } = render(<Chip>Spezial</Chip>)
    expect(screen.getByText('Spezial')).toBeInTheDocument()
    expect(container.querySelector('.ui-chip__swatch')).not.toBeInTheDocument()
  })

  it('renders a static chip as text, not as a control', () => {
    render(<Chip state="frei" />)
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })

  it('renders an interactive chip as a 44 px button with focus', async () => {
    const onClick = vi.fn()
    render(<Chip state="frei" onClick={onClick} />)
    const button = screen.getByRole('button', { name: 'frei' })
    expect(button).toHaveClass('ui-target')
    await userEvent.tab()
    expect(button).toHaveFocus()
    await userEvent.keyboard('{Enter}')
    expect(onClick).toHaveBeenCalledOnce()
  })
})

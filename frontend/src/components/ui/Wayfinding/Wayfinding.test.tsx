import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { Wayfinding } from './Wayfinding'

describe('Wayfinding', () => {
  it('presents target and direction as text', () => {
    const { container } = render(<Wayfinding direction="hoch" target="Aufzug" />)
    expect(container).toHaveTextContent('Aufzug · hoch')
    expect(container.querySelector('svg')).toHaveAttribute('aria-hidden', 'true')
  })

  it('appends distance when given', () => {
    const { container } = render(<Wayfinding direction="links" target="Raum 2.14" distance="ca. 40 m" />)
    expect(container).toHaveTextContent('Raum 2.14 · links · ca. 40 m')
  })

  it('is plain text without href or onClick', () => {
    render(<Wayfinding direction="geradeaus" target="Treppe" />)
    expect(screen.queryByRole('link')).not.toBeInTheDocument()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })

  it('renders a link with 44 px target when href is given', () => {
    render(<Wayfinding direction="rechts" target="Etage 2" href="/etage/2" />)
    const link = screen.getByRole('link', { name: /Etage 2/ })
    expect(link).toHaveAttribute('href', '/etage/2')
    expect(link).toHaveClass('ui-target')
  })

  it('renders a button when onClick is given', async () => {
    const onClick = vi.fn()
    render(<Wayfinding direction="runter" target="Ausgang" onClick={onClick} />)
    await userEvent.click(screen.getByRole('button', { name: /Ausgang/ }))
    expect(onClick).toHaveBeenCalledOnce()
  })
})

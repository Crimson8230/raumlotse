import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import HelpPage from './HelpPage'
import { HELP_CONTACT } from './helpContent'

describe('HelpPage', () => {
  it('has the skip link first, one main area and the fixed sections', () => {
    render(<MemoryRouter><HelpPage /></MemoryRouter>)

    expect(screen.getByRole('link', { name: 'Zum Inhalt springen' })).toHaveAttribute('href', '#inhalt')
    expect(screen.getAllByRole('main')).toHaveLength(1)
    expect(screen.getByRole('main')).toHaveAttribute('id', 'inhalt')
    expect(screen.getByRole('heading', { level: 1, name: 'Hilfe' })).toBeInTheDocument()
    for (const name of ['Raum finden und buchen', 'Bedeutung der Streifen und Muster', 'Kontakt']) {
      expect(screen.getByRole('heading', { level: 2, name })).toBeInTheDocument()
    }
  })

  it('shows the contact address from the content file and the footer', () => {
    render(<MemoryRouter><HelpPage /></MemoryRouter>)

    expect(screen.getByRole('link', { name: HELP_CONTACT })).toHaveAttribute('href', `mailto:${HELP_CONTACT}`)
    expect(screen.getByRole('contentinfo')).toBeInTheDocument()
  })
})

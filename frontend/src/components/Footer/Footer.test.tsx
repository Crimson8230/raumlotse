import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { Footer } from './Footer'

describe('Footer', () => {
  it('offers the help link as a footer landmark', () => {
    render(<MemoryRouter><Footer /></MemoryRouter>)

    const footer = screen.getByRole('contentinfo')
    expect(footer).toContainElement(screen.getByRole('link', { name: 'Hilfe' }))
    expect(screen.getByRole('link', { name: 'Hilfe' })).toHaveAttribute('href', '/hilfe')
  })
})

import { MemoryRouter } from 'react-router-dom'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { Navigation } from './Navigation'

function renderNav(initialPath: string) {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <Navigation />
    </MemoryRouter>,
  )
}

describe('Navigation', () => {
  it('renders links to all main areas', () => {
    renderNav('/')

    expect(screen.getByRole('link', { name: 'Home' })).toHaveAttribute('href', '/')
    expect(screen.getByRole('link', { name: 'Standorte' })).toHaveAttribute('href', '/locations')
    expect(screen.getByRole('link', { name: 'Räume' })).toHaveAttribute('href', '/rooms')
  })

  it('marks the matching top-level item active for a nested route, and no other item', () => {
    renderNav('/rooms/some-id')

    expect(screen.getByRole('link', { name: 'Räume' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('link', { name: 'Home' })).not.toHaveAttribute('aria-current')
    expect(screen.getByRole('link', { name: 'Standorte' })).not.toHaveAttribute('aria-current')
  })

  it('activates the clicked link, deactivating the previous one', async () => {
    const user = userEvent.setup()
    renderNav('/')

    expect(screen.getByRole('link', { name: 'Home' })).toHaveAttribute('aria-current', 'page')

    await user.click(screen.getByRole('link', { name: 'Standorte' }))

    expect(screen.getByRole('link', { name: 'Standorte' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('link', { name: 'Home' })).not.toHaveAttribute('aria-current')
  })
})

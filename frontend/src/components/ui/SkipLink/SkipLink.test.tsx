import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { SkipLink } from './SkipLink'

function Frame({ targetId }: { targetId?: string }) {
  return (
    <>
      <SkipLink targetId={targetId} />
      <nav>
        <a href="/start">Start</a>
      </nav>
      <main id={targetId ?? 'inhalt'}>Inhalt</main>
    </>
  )
}

describe('SkipLink', () => {
  it('is the first focusable element and shows its default text', async () => {
    render(<Frame />)
    await userEvent.tab()
    const link = screen.getByRole('link', { name: 'Zum Inhalt springen' })
    expect(link).toHaveFocus()
    expect(link).toHaveClass('ui-skip-link')
  })

  it('moves focus to the main content when activated', async () => {
    render(<Frame />)
    await userEvent.tab()
    await userEvent.keyboard('{Enter}')
    expect(screen.getByRole('main')).toHaveFocus()
    expect(screen.getByRole('main')).toHaveAttribute('tabindex', '-1')
  })

  it('supports a custom target id', async () => {
    render(<Frame targetId="haupt" />)
    await userEvent.tab()
    await userEvent.keyboard('{Enter}')
    expect(screen.getByRole('main')).toHaveFocus()
  })

  it('stays in the accessibility tree while not focused', () => {
    render(<Frame />)
    expect(screen.getByRole('link', { name: 'Zum Inhalt springen' })).toBeInTheDocument()
  })
})

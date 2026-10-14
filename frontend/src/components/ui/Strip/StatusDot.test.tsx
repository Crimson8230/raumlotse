import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { StatusDot } from './StatusDot'

describe('StatusDot', () => {
  it('shows the state as pattern and as a word for assistive technology', () => {
    const { container } = render(<StatusDot state="belegt" />)
    expect(container.querySelector('.ui-pattern-hatch')).toBeInTheDocument()
    expect(screen.getByText('belegt')).toHaveClass('ui-sr-only')
  })

  it('uses a different pattern for each state', () => {
    const { container } = render(<StatusDot state="gesperrt" />)
    expect(container.querySelector('.ui-pattern-dotted')).toBeInTheDocument()
    expect(container.querySelector('.ui-pattern-hatch')).not.toBeInTheDocument()
  })
})

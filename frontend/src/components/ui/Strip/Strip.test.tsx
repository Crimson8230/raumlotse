import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Strip } from './Strip'

const at = (hour: number, minute = 0) => new Date(2026, 9, 14, hour, minute)
const range = { rangeStart: at(8), rangeEnd: at(18) }

function label(container: HTMLElement): string {
  return container.querySelector('[role="img"]')?.getAttribute('aria-label') ?? ''
}

describe('Strip', () => {
  it('shows free time empty and booked time hatched, with each state as a word', () => {
    const { container } = render(
      <Strip {...range} now={at(9, 15)} windows={[{ start: at(9), end: at(10), state: 'belegt' }]} />,
    )
    expect(container.querySelector('.ui-pattern-hatch')).toBeInTheDocument()
    expect(container.querySelector('.ui-pattern-empty')).toBeInTheDocument()
    expect(label(container)).toContain('08:00–09:00 frei')
    expect(label(container)).toContain('09:00–10:00 belegt')
    expect(label(container)).toContain('10:00–18:00 frei')
  })

  it('places the Jetzt marker at the matching position and announces it', () => {
    const { container } = render(
      <Strip {...range} now={at(9, 15)} windows={[{ start: at(9), end: at(10), state: 'belegt' }]} />,
    )
    const marker = container.querySelector<HTMLElement>('.ui-strip__now')
    expect(marker).toBeInTheDocument()
    expect(marker?.style.left).toBe('12.5%')
    expect(marker).toHaveTextContent('Jetzt')
    expect(label(container)).toContain('Jetzt: 09:15, belegt')
  })

  it('reads as free for the whole range when there are no bookings', () => {
    const { container } = render(<Strip {...range} now={at(9)} windows={[]} />)
    expect(label(container)).toContain('ganztägig frei')
    expect(container.querySelectorAll('.ui-strip__segment')).toHaveLength(1)
  })

  it('shows no marker when the current time is outside the range', () => {
    const { container } = render(<Strip {...range} now={at(19)} windows={[]} />)
    expect(container.querySelector('.ui-strip__now')).not.toBeInTheDocument()
    expect(label(container)).not.toContain('Jetzt')
  })

  it('uses solid ink for own and dotted for blocked windows, each with its word', () => {
    const { container } = render(
      <Strip
        {...range}
        windows={[
          { start: at(9), end: at(10), state: 'meine' },
          { start: at(12), end: at(13), state: 'gesperrt' },
        ]}
      />,
    )
    expect(container.querySelector('.ui-pattern-solid')).toBeInTheDocument()
    expect(container.querySelector('.ui-pattern-dotted')).toBeInTheDocument()
    expect(label(container)).toContain('09:00–10:00 meine')
    expect(label(container)).toContain('12:00–13:00 gesperrt')
  })

  it('merges adjacent windows of the same state', () => {
    const { container } = render(
      <Strip
        {...range}
        windows={[
          { start: at(9), end: at(10), state: 'belegt' },
          { start: at(10), end: at(11), state: 'belegt' },
        ]}
      />,
    )
    expect(label(container)).toContain('09:00–11:00 belegt')
    expect(label(container)).not.toContain('09:00–10:00 belegt')
  })

  it('handles a window ending exactly at the range end and clamps windows to the range', () => {
    const { container } = render(
      <Strip
        {...range}
        windows={[
          { start: at(17), end: at(18), state: 'belegt' },
          { start: at(7), end: at(9), state: 'gesperrt' },
        ]}
      />,
    )
    expect(label(container)).toContain('17:00–18:00 belegt')
    expect(label(container)).toContain('08:00–09:00 gesperrt')
    const last = [...container.querySelectorAll<HTMLElement>('.ui-strip__segment')].at(-1)
    expect(last?.style.left).toBe('90%')
    expect(last?.style.width).toBe('10%')
  })

  it('ignores zero-length windows', () => {
    const { container } = render(
      <Strip {...range} windows={[{ start: at(12), end: at(12), state: 'belegt' }]} />,
    )
    expect(label(container)).not.toContain('belegt')
  })

  it('lets the stricter state win on overlap', () => {
    const { container } = render(
      <Strip
        {...range}
        windows={[
          { start: at(9), end: at(11), state: 'belegt' },
          { start: at(10), end: at(12), state: 'gesperrt' },
        ]}
      />,
    )
    expect(label(container)).toContain('09:00–10:00 belegt')
    expect(label(container)).toContain('10:00–12:00 gesperrt')
  })

  it('keeps the accessible name bounded for very many windows', () => {
    const windows = Array.from({ length: 15 }, (_, i) => ({
      start: at(8, i * 30),
      end: at(8, i * 30 + 10),
      state: 'belegt' as const,
    }))
    const { container } = render(
      <Strip rangeStart={at(8)} rangeEnd={at(18)} windows={windows} />,
    )
    expect(label(container)).toMatch(/und \d+ weitere Zeitfenster/)
  })

  it('shows the room name as a heading-like text and prefixes the accessible name', () => {
    const { container } = render(<Strip {...range} name="Seminarraum 2.14" windows={[]} />)
    expect(screen.getByText('Seminarraum 2.14')).toHaveClass('ui-strip__name')
    expect(label(container)).toMatch(/^Seminarraum 2\.14: /)
    expect(screen.getByRole('img')).toBeInTheDocument()
  })
})

import { render } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { RoomName, TimeText } from './Typography'

describe('Typography', () => {
  it('renders room names in the serif class', () => {
    const { container } = render(<RoomName>Raum 2.14</RoomName>)
    const span = container.querySelector('span.room-name')
    expect(span).toHaveTextContent('Raum 2.14')
    expect(container.textContent).toBe('Raum 2.14')
  })

  it('renders times and numbers in the mono class', () => {
    const { container } = render(<TimeText>10:00–12:00</TimeText>)
    expect(container.querySelector('span.mono')).toHaveTextContent('10:00–12:00')
    expect(container.textContent).toBe('10:00–12:00')
  })
})

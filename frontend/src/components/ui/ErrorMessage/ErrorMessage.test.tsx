import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { ErrorMessage } from './ErrorMessage'

describe('ErrorMessage', () => {
  it('shows symbol, the word "Fehler:" and the text', () => {
    const { container } = render(<ErrorMessage>Der Raum ist belegt.</ErrorMessage>)
    const alert = screen.getByRole('alert')
    expect(alert).toHaveTextContent('Fehler: Der Raum ist belegt.')
    expect(container.querySelector('svg')).toHaveAttribute('aria-hidden', 'true')
  })

  it('shows the user-facing label instead of a technical field name', () => {
    render(<ErrorMessage field="startTime">darf nicht leer sein</ErrorMessage>)
    expect(screen.getByRole('alert')).toHaveTextContent('Fehler: Beginn: darf nicht leer sein')
    expect(screen.getByRole('alert')).not.toHaveTextContent('startTime')
  })

  it('appears as an alert when it is added after the initial render', () => {
    const { rerender } = render(<div>{null}</div>)
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    rerender(
      <div>
        <ErrorMessage>Speichern fehlgeschlagen.</ErrorMessage>
      </div>,
    )
    expect(screen.getByRole('alert')).toHaveTextContent('Speichern fehlgeschlagen.')
  })

  it('passes an id for aria-describedby', () => {
    render(<ErrorMessage id="err-1">Text</ErrorMessage>)
    expect(screen.getByRole('alert')).toHaveAttribute('id', 'err-1')
  })
})

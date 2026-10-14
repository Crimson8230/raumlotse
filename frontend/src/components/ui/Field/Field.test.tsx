import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { Field } from './Field'

describe('Field', () => {
  it('shows a visible label that focuses the input when clicked', async () => {
    render(<Field label="Raumname">{(control) => <input {...control} />}</Field>)
    await userEvent.click(screen.getByText('Raumname'))
    expect(screen.getByLabelText('Raumname')).toHaveFocus()
  })

  it('marks a required field visibly and in words', () => {
    render(
      <Field label="Raumname" required>
        {(control) => <input {...control} />}
      </Field>,
    )
    expect(screen.getByText('Pflichtfeld')).toBeVisible()
    expect(screen.getByLabelText('Raumname')).toHaveAttribute('aria-required', 'true')
  })

  it('reads the hint together with the label', () => {
    render(
      <Field label="Raumname" hint="Höchstens 40 Zeichen">
        {(control) => <input {...control} />}
      </Field>,
    )
    expect(screen.getByLabelText('Raumname')).toHaveAccessibleDescription('Höchstens 40 Zeichen')
  })

  it('connects the error to the input and marks it invalid', () => {
    render(
      <Field label="Raumname" error="darf nicht leer sein">
        {(control) => <input {...control} />}
      </Field>,
    )
    const input = screen.getByLabelText('Raumname')
    expect(input).toHaveAttribute('aria-invalid', 'true')
    expect(input).toHaveAccessibleDescription(/Fehler: darf nicht leer sein/)
  })

  it('announces the error before the hint when both exist', () => {
    render(
      <Field label="Raumname" hint="Höchstens 40 Zeichen" error="zu lang">
        {(control) => <input {...control} />}
      </Field>,
    )
    const description = screen.getByLabelText('Raumname').getAttribute('aria-describedby') ?? ''
    const [first, second] = description.split(' ')
    expect(document.getElementById(first)).toHaveTextContent('Fehler: zu lang')
    expect(document.getElementById(second)).toHaveTextContent('Höchstens 40 Zeichen')
  })

  it('leaves no dangling references when hint and error are absent', () => {
    render(<Field label="Raumname">{(control) => <input {...control} />}</Field>)
    const input = screen.getByLabelText('Raumname')
    expect(input).not.toHaveAttribute('aria-describedby')
    expect(input).not.toHaveAttribute('aria-invalid')
    expect(input).not.toHaveAttribute('aria-required')
  })

  it('works with select and textarea controls', () => {
    render(
      <>
        <Field label="Stockwerk">
          {(control) => (
            <select {...control}>
              <option>EG</option>
            </select>
          )}
        </Field>
        <Field label="Notiz">{(control) => <textarea {...control} />}</Field>
      </>,
    )
    expect(screen.getByLabelText('Stockwerk')).toBeInTheDocument()
    expect(screen.getByLabelText('Notiz')).toBeInTheDocument()
  })
})

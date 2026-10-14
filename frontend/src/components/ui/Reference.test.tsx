import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import {
  Chip,
  DataList,
  DataTable,
  ErrorMessage,
  Field,
  SkipLink,
  Strip,
  StatusDot,
  Wayfinding,
} from './index'

const at = (hour: number, minute = 0) => new Date(2026, 9, 14, hour, minute)

interface Room {
  id: string
  name: string
  seats: number
}

const rooms: Room[] = [
  { id: 'r1', name: 'Seminarraum 2.14', seats: 24 },
  { id: 'r2', name: 'Hörsaal 1', seats: 120 },
]

/** Referenzseite (SC-006, FR-014): nur Bausteine, keine eigenen Stile. */
function ReferencePage() {
  return (
    <>
      <SkipLink />
      <main id="inhalt">
        <h1>Räume</h1>
        <DataList
          aria-label="Räume heute"
          items={rooms}
          itemKey={(room) => room.id}
          emptyText="Keine Räume."
          renderItem={(room) => (
            <>
              <StatusDot state="frei" />
              <Strip
                name={room.name}
                rangeStart={at(8)}
                rangeEnd={at(18)}
                now={at(9, 15)}
                windows={[{ start: at(9), end: at(10), state: 'belegt' }]}
              />
              <Chip state="belegt" />
              <Wayfinding direction="hoch" target="Etage 2" distance="ca. 40 m" />
            </>
          )}
        />
        <DataTable
          caption="Raumübersicht"
          columns={[
            { key: 'name', header: 'Raum', cell: (room: Room) => room.name },
            { key: 'seats', header: 'Plätze', mono: true, cell: (room: Room) => room.seats },
          ]}
          rows={rooms}
          rowKey={(room) => room.id}
          rowHeader="name"
          emptyText="Keine Räume."
        />
        <form>
          <Field label="Raumname" required hint="Höchstens 40 Zeichen" error="darf nicht leer sein">
            {(control) => <input {...control} />}
          </Field>
          <ErrorMessage field="startTime">darf nicht null sein</ErrorMessage>
        </form>
      </main>
    </>
  )
}

describe('reference page', () => {
  it('is assembled from the blocks and exposes the expected structure', async () => {
    render(<ReferencePage />)
    expect(within(screen.getByRole('list', { name: 'Räume heute' })).getAllByRole('listitem')).toHaveLength(2)
    expect(screen.getAllByRole('img')).toHaveLength(2)
    expect(screen.getByRole('table', { name: 'Raumübersicht' })).toBeInTheDocument()
    expect(screen.getByLabelText('Raumname')).toHaveAccessibleDescription(/Fehler: darf nicht leer sein/)
    expect(screen.getAllByText('Fehler:', { selector: 'strong' })).toHaveLength(2)
    await userEvent.tab()
    expect(screen.getByRole('link', { name: 'Zum Inhalt springen' })).toHaveFocus()
  })
})

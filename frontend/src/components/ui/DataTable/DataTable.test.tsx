import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { DataTable } from './DataTable'

interface Row {
  id: string
  name: string
  seats: number
}

const rows: Row[] = [
  { id: 'a', name: 'Seminarraum 1', seats: 24 },
  { id: 'b', name: 'Hörsaal 2', seats: 120 },
]

const columns = [
  { key: 'name', header: 'Raum', cell: (row: Row) => row.name },
  { key: 'seats', header: 'Plätze', mono: true, cell: (row: Row) => row.seats },
]

describe('DataTable', () => {
  it('has a caption and column headers with scope', () => {
    render(<DataTable caption="Räume" columns={columns} rows={rows} rowKey={(row) => row.id} emptyText="Leer" />)
    const table = screen.getByRole('table', { name: 'Räume' })
    expect(within(table).getByText('Räume', { selector: 'caption' })).toBeInTheDocument()
    const headers = within(table).getAllByRole('columnheader')
    expect(headers.map((header) => header.textContent)).toEqual(['Raum', 'Plätze'])
    for (const header of headers) expect(header).toHaveAttribute('scope', 'col')
  })

  it('renders the designated column as row header', () => {
    render(
      <DataTable
        caption="Räume"
        columns={columns}
        rows={rows}
        rowKey={(row) => row.id}
        rowHeader="name"
        emptyText="Leer"
      />,
    )
    const rowHeaders = screen.getAllByRole('rowheader')
    expect(rowHeaders.map((header) => header.textContent)).toEqual(['Seminarraum 1', 'Hörsaal 2'])
    for (const header of rowHeaders) expect(header).toHaveAttribute('scope', 'row')
  })

  it('shows a worded empty state', () => {
    render(
      <DataTable caption="Räume" columns={columns} rows={[]} rowKey={(row: Row) => row.id} emptyText="Keine Räume." />,
    )
    expect(screen.getByText('Keine Räume.')).toBeInTheDocument()
  })

  it('scrolls inside a keyboard-reachable region on narrow screens, not the page', () => {
    render(<DataTable caption="Räume" columns={columns} rows={rows} rowKey={(row) => row.id} emptyText="Leer" />)
    const region = screen.getByRole('region', { name: 'Räume' })
    expect(region).toHaveAttribute('tabindex', '0')
    expect(region).toHaveClass('ui-table-wrap')
  })

  it('keeps long cell content complete for assistive technology', () => {
    const long = 'Sehr langer Raumname '.repeat(10).trim()
    render(
      <DataTable
        caption="Räume"
        columns={columns}
        rows={[{ id: 'x', name: long, seats: 1 }]}
        rowKey={(row) => row.id}
        emptyText="Leer"
      />,
    )
    expect(screen.getByText(long)).toBeInTheDocument()
  })
})

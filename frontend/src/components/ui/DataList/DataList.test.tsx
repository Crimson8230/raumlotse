import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { DataList } from './DataList'

const rooms = [
  { id: 'a', name: 'Seminarraum 1' },
  { id: 'b', name: 'Hörsaal 2' },
]

describe('DataList', () => {
  it('renders a real list with one item per entry', () => {
    render(
      <DataList
        aria-label="Räume"
        items={rooms}
        itemKey={(room) => room.id}
        renderItem={(room) => room.name}
        emptyText="Keine Räume vorhanden."
      />,
    )
    const list = screen.getByRole('list', { name: 'Räume' })
    expect(within(list).getAllByRole('listitem')).toHaveLength(2)
    expect(within(list).getByText('Hörsaal 2')).toBeInTheDocument()
  })

  it('shows a worded empty state instead of an empty list', () => {
    render(
      <DataList
        items={[]}
        itemKey={(room: { id: string }) => room.id}
        renderItem={() => null}
        emptyText="Keine Räume vorhanden."
      />,
    )
    expect(screen.getByText('Keine Räume vorhanden.')).toBeInTheDocument()
    expect(screen.queryByRole('list')).not.toBeInTheDocument()
  })
})

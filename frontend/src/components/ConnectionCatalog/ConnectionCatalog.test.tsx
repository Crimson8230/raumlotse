import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ConnectionCatalog } from './ConnectionCatalog'
import * as mapsApi from '../../API/maps'
import { ApiError } from '../../API/client'
import type { Connection } from '../../types/map'

vi.mock('../../API/maps')
const api = vi.mocked(mapsApi)

const elevator: Connection = {
  id: 'c1', name: 'Aufzug A', type: 'ELEVATOR', incomplete: false,
  points: [{ mapId: 'm0', x: 0.2, y: 0.3 }, { mapId: 'm1', x: 0.6, y: 0.7 }],
}
const stairs: Connection = { id: 'c2', name: 'Treppe Nord', type: 'STAIRS', incomplete: true, points: [] }

function setup(props: Partial<Parameters<typeof ConnectionCatalog>[0]> = {}) {
  const handlers = { onChanged: vi.fn(), onSelectForPlacement: vi.fn() }
  render(
    <ConnectionCatalog
      connections={[elevator, stairs]}
      currentMapId="m0"
      admin
      activeConnectionId={undefined}
      {...handlers}
      {...props}
    />,
  )
  return handlers
}

beforeEach(() => vi.resetAllMocks())

describe('ConnectionCatalog', () => {
  it('lists connections with type and an incomplete badge', () => {
    setup()
    expect(screen.getByText('Aufzug A')).toBeInTheDocument()
    expect(screen.getAllByText(/unvollständig/i)).toHaveLength(1)
  })

  it('lets an admin create a connection', async () => {
    const user = userEvent.setup()
    api.createConnection.mockResolvedValue({ ...stairs, id: 'c3', name: 'Treppe Süd' })
    const { onChanged } = setup()

    await user.type(screen.getByLabelText(/name der verbindung/i), 'Treppe Süd')
    await user.selectOptions(screen.getByLabelText(/typ/i), 'STAIRS')
    await user.click(screen.getByRole('button', { name: /verbindung anlegen/i }))

    await waitFor(() => expect(api.createConnection).toHaveBeenCalledWith({ name: 'Treppe Süd', type: 'STAIRS' }))
    expect(onChanged).toHaveBeenCalled()
  })

  it('shows the server message when the name is already taken', async () => {
    const user = userEvent.setup()
    api.createConnection.mockRejectedValue(new ApiError(409, { title: 'Conflict', status: 409, detail: 'Name already used.' }))
    setup()

    await user.type(screen.getByLabelText(/name der verbindung/i), 'Aufzug A')
    await user.click(screen.getByRole('button', { name: /verbindung anlegen/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Name already used.')
  })

  it('selects a connection for placing its point on the current map', async () => {
    const user = userEvent.setup()
    const { onSelectForPlacement } = setup()
    await user.click(screen.getByRole('button', { name: /punkt setzen: treppe nord/i }))
    expect(onSelectForPlacement).toHaveBeenCalledWith('c2')
  })

  it('removes the point on the current map only when the connection has one here', async () => {
    const user = userEvent.setup()
    api.deleteConnectionPoint.mockResolvedValue(undefined)
    const { onChanged } = setup()

    expect(screen.queryByRole('button', { name: /punkt entfernen: treppe nord/i })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /punkt entfernen: aufzug a/i }))

    await waitFor(() => expect(api.deleteConnectionPoint).toHaveBeenCalledWith('c1', 'm0'))
    expect(onChanged).toHaveBeenCalled()
  })

  it('renames a connection and deletes it after confirmation', async () => {
    const user = userEvent.setup()
    api.updateConnection.mockResolvedValue(elevator)
    api.deleteConnection.mockResolvedValue(undefined)
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    setup()

    await user.click(screen.getByRole('button', { name: /bearbeiten: aufzug a/i }))
    const input = screen.getByLabelText(/^name$/i)
    await user.clear(input)
    await user.type(input, 'Aufzug Ost')
    await user.click(screen.getByRole('button', { name: /speichern/i }))
    await waitFor(() => expect(api.updateConnection).toHaveBeenCalledWith('c1', { name: 'Aufzug Ost', type: 'ELEVATOR' }))

    await user.click(screen.getByRole('button', { name: /löschen: treppe nord/i }))
    await waitFor(() => expect(api.deleteConnection).toHaveBeenCalledWith('c2'))
  })

  it('is read-only for non-admins', () => {
    setup({ admin: false })
    expect(screen.getByText('Aufzug A')).toBeInTheDocument()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/name der verbindung/i)).not.toBeInTheDocument()
  })
})

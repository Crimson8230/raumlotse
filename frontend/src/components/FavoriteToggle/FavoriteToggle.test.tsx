import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { FavoriteToggle } from './FavoriteToggle'
import * as favoritesApi from '../../API/favoriteRooms'
import { ApiError } from '../../API/client'

vi.mock('../../API/favoriteRooms')
const favorites = vi.mocked(favoritesApi)

function deferred() {
  let resolve!: () => void
  let reject!: (err: unknown) => void
  const promise = new Promise<void>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

beforeEach(() => {
  vi.resetAllMocks()
})

describe('FavoriteToggle', () => {
  it('shows the current state with aria-pressed and an accessible name', () => {
    const { rerender } = render(<FavoriteToggle roomId="r1" roomName="Seminar" isFavorite={false} onChanged={vi.fn()} />)
    expect(screen.getByRole('button', { name: 'Seminar als Favorit markieren' })).toHaveAttribute('aria-pressed', 'false')

    rerender(<FavoriteToggle roomId="r1" roomName="Seminar" isFavorite onChanged={vi.fn()} />)
    expect(screen.getByRole('button', { name: 'Seminar: Favorit entfernen' })).toHaveAttribute('aria-pressed', 'true')
  })

  it('marks optimistically and reports the change once the server confirms', async () => {
    const pending = deferred()
    favorites.markFavoriteRoom.mockReturnValue(pending.promise)
    const onChanged = vi.fn()
    const user = userEvent.setup()
    render(<FavoriteToggle roomId="r1" roomName="Seminar" isFavorite={false} onChanged={onChanged} />)

    await user.click(screen.getByRole('button'))

    expect(screen.getByRole('button')).toHaveAttribute('aria-pressed', 'true')
    expect(onChanged).not.toHaveBeenCalled()
    pending.resolve()
    await vi.waitFor(() => expect(onChanged).toHaveBeenCalledWith('r1', true))
    expect(favorites.markFavoriteRoom).toHaveBeenCalledWith('r1')
  })

  it('unmarks a favorite', async () => {
    favorites.unmarkFavoriteRoom.mockResolvedValue(undefined)
    const onChanged = vi.fn()
    const user = userEvent.setup()
    render(<FavoriteToggle roomId="r1" roomName="Seminar" isFavorite onChanged={onChanged} />)

    await user.click(screen.getByRole('button'))

    await vi.waitFor(() => expect(onChanged).toHaveBeenCalledWith('r1', false))
    expect(favorites.unmarkFavoriteRoom).toHaveBeenCalledWith('r1')
  })

  it('reverts and explains the limit when the server refuses an eleventh favorite', async () => {
    favorites.markFavoriteRoom.mockRejectedValue(
      new ApiError(409, {
        title: 'Conflict',
        status: 409,
        detail: 'Höchstens 10 Favoriten – entferne zuerst einen anderen.',
        code: 'FAVORITE_LIMIT_REACHED',
      }),
    )
    const onChanged = vi.fn()
    const user = userEvent.setup()
    render(<FavoriteToggle roomId="r1" roomName="Seminar" isFavorite={false} onChanged={onChanged} />)

    await user.click(screen.getByRole('button'))

    expect(await screen.findByRole('alert')).toHaveTextContent('Höchstens 10 Favoriten – entferne zuerst einen anderen.')
    expect(screen.getByRole('button')).toHaveAttribute('aria-pressed', 'false')
    expect(onChanged).not.toHaveBeenCalled()
  })

  it('reverts and shows other errors', async () => {
    favorites.markFavoriteRoom.mockRejectedValue(new Error('Netzwerkfehler'))
    const user = userEvent.setup()
    render(<FavoriteToggle roomId="r1" roomName="Seminar" isFavorite={false} onChanged={vi.fn()} />)

    await user.click(screen.getByRole('button'))

    expect(await screen.findByRole('alert')).toHaveTextContent('Netzwerkfehler')
    expect(screen.getByRole('button')).toHaveAttribute('aria-pressed', 'false')
  })
})

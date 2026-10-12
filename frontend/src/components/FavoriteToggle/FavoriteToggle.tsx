import { useState } from 'react'
import { Star } from 'lucide-react'
import { formatApiError } from '../../API/client'
import { markFavoriteRoom, unmarkFavoriteRoom } from '../../API/favoriteRooms'
import './FavoriteToggle.css'

interface FavoriteToggleProps {
  roomId: string
  roomName: string
  isFavorite: boolean
  /** Called after the server confirmed the change, e.g. to re-run the ranked search (feature 017, FR-011). */
  onChanged: (roomId: string, favorite: boolean) => void
}

/**
 * Star button for a personal favorite room. Toggles optimistically and reverts when the server refuses,
 * e.g. with FAVORITE_LIMIT_REACHED for an eleventh favorite (FR-006a); the server's message is shown inline.
 */
export function FavoriteToggle({ roomId, roomName, isFavorite, onChanged }: FavoriteToggleProps) {
  const [pending, setPending] = useState<boolean | null>(null)
  const [error, setError] = useState<string | null>(null)
  const shown = pending ?? isFavorite

  async function toggle() {
    const next = !shown
    setError(null)
    setPending(next)
    try {
      await (next ? markFavoriteRoom(roomId) : unmarkFavoriteRoom(roomId))
      onChanged(roomId, next)
    } catch (err) {
      setError(formatApiError(err))
    } finally {
      setPending(null)
    }
  }

  return (
    <span className="favorite-toggle">
      <button
        type="button"
        aria-pressed={shown}
        aria-label={shown ? `${roomName}: Favorit entfernen` : `${roomName} als Favorit markieren`}
        onClick={toggle}
        disabled={pending !== null}
      >
        <Star aria-hidden="true" fill={shown ? 'currentColor' : 'none'} />
      </button>
      {error && (
        <span role="alert" className="feedback-error">
          {error}
        </span>
      )}
    </span>
  )
}

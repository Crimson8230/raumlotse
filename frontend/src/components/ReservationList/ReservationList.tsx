import { useCallback, useEffect, useState } from 'react'
import {
  activateReservation,
  cancelReservation,
  completeReservation,
  expireReservation,
  listRoomReservations,
  updateReservationMetadata,
} from '../../API/reservations'
import { formatApiError } from '../../API/client'
import { formatDateTime, formatDateTimeRange } from '../../utils/date'
import type { Room } from '../../types/room'
import type { Reservation } from '../../types/reservation'
import './ReservationList.css'

export interface ReservationListProps {
  room: Room
  refreshSignal?: number
  onReservationChanged?: () => void
}

export function ReservationList({
  room,
  refreshSignal,
  onReservationChanged,
}: ReservationListProps) {
  const [reservations, setReservations] = useState<Reservation[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [editAttendees, setEditAttendees] = useState<number | ''>(1)
  const [editNote, setEditNote] = useState<string>('')
  const [actionInProgress, setActionInProgress] = useState<string | null>(null)

  const reload = useCallback(async () => {
    try {
      const data = await listRoomReservations(room.id)
      setReservations(data)
    } catch (err) {
      setError(formatApiError(err))
    }
  }, [room.id])

  useEffect(() => {
    let ignore = false
    listRoomReservations(room.id)
      .then((data) => {
        if (!ignore) {
          setReservations(data)
          setLoading(false)
        }
      })
      .catch((err) => {
        if (!ignore) {
          setError(formatApiError(err))
          setLoading(false)
        }
      })
    return () => {
      ignore = true
    }
  }, [room.id, refreshSignal])

  function handleStartEdit(res: Reservation) {
    setEditingId(res.id)
    setEditAttendees(res.expectedAttendees)
    setEditNote(res.note ?? '')
    setError(null)
  }

  function handleCancelEdit() {
    setEditingId(null)
    setError(null)
  }

  async function handleSaveEdit(res: Reservation) {
    const attendeesNum = Number(editAttendees)
    if (!editAttendees || isNaN(attendeesNum) || attendeesNum < 1) {
      setError('Expected attendees must be a positive integer greater than or equal to 1.')
      return
    }
    if (attendeesNum > res.seatingArrangement.maxCapacity) {
      setError(
        `Expected attendees (${attendeesNum}) cannot exceed arrangement capacity (${res.seatingArrangement.maxCapacity}).`,
      )
      return
    }

    setActionInProgress(res.id)
    setError(null)
    try {
      await updateReservationMetadata(res.id, {
        expectedAttendees: attendeesNum,
        note: editNote.trim() || undefined,
      })
      setEditingId(null)
      await reload()
      onReservationChanged?.()
    } catch (err) {
      setError(formatApiError(err))
    } finally {
      setActionInProgress(null)
    }
  }

  async function handleAction(actionName: string, actionFn: () => Promise<Reservation>) {
    setActionInProgress(actionName)
    setError(null)
    try {
      await actionFn()
      await reload()
      onReservationChanged?.()
    } catch (err) {
      setError(formatApiError(err))
    } finally {
      setActionInProgress(null)
    }
  }

  if (loading) {
    return <p className="status-loading">Loading reservations…</p>
  }

  return (
    <div className="reservation-list-container">
      {error && (
        <p role="alert" className="feedback-error">
          {error}
        </p>
      )}

      {reservations.length === 0 ? (
        <p className="status-empty">No reservations found for this room.</p>
      ) : (
        <ul className="list-plain reservation-list">
          {reservations.map((res) => {
            const isEditing = editingId === res.id
            const isBusy = actionInProgress === res.id

            return (
              <li key={res.id} className="panel reservation-card">
                <div className="reservation-header">
                  <span className={`status-badge status-${res.status.toLowerCase()}`}>
                    {res.status}
                  </span>
                  <span className="reservation-time">
                    {formatDateTimeRange(res.startTime, res.endTime)}
                  </span>
                </div>

                <div className="reservation-details">
                  <p>
                    <strong>Layout:</strong> {res.seatingArrangement.name} (Max:{' '}
                    {res.seatingArrangement.maxCapacity})
                  </p>
                  <p>
                    <strong>Attendees:</strong> {res.expectedAttendees}
                  </p>
                  <p>
                    <strong>Booked by:</strong> {res.createdBy}
                  </p>
                  <p className="reservation-created-at">
                    <small>Created: {formatDateTime(res.createdAt)}</small>
                  </p>
                  {res.additionalEquipment && res.additionalEquipment.length > 0 && (
                    <p>
                      <strong>Additional Equipment:</strong>{' '}
                      {res.additionalEquipment.map((eq) => eq.name).join(', ')}
                    </p>
                  )}
                  {res.note && (
                    <p className="reservation-note">
                      <strong>Notes:</strong> {res.note}
                    </p>
                  )}
                </div>

                {isEditing ? (
                  <form
                    className="reservation-edit-form"
                    onSubmit={(e) => {
                      e.preventDefault()
                      handleSaveEdit(res)
                    }}
                  >
                    <div>
                      <label htmlFor={`edit-attendees-${res.id}`}>Edit Attendees</label>
                      <input
                        id={`edit-attendees-${res.id}`}
                        type="number"
                        min="1"
                        max={res.seatingArrangement.maxCapacity}
                        required
                        value={editAttendees}
                        onChange={(e) =>
                          setEditAttendees(
                            e.target.value === '' ? '' : parseInt(e.target.value, 10),
                          )
                        }
                      />
                    </div>
                    <div>
                      <label htmlFor={`edit-notes-${res.id}`}>Edit Notes</label>
                      <textarea
                        id={`edit-notes-${res.id}`}
                        rows={2}
                        maxLength={2000}
                        value={editNote}
                        onChange={(e) => setEditNote(e.target.value)}
                      />
                    </div>
                    <div className="actions">
                      <button type="submit" disabled={isBusy}>
                        Save
                      </button>
                      <button type="button" onClick={handleCancelEdit} disabled={isBusy}>
                        Cancel
                      </button>
                    </div>
                  </form>
                ) : (
                  <div className="actions reservation-actions">
                    {res.status === 'RESERVED' && (
                      <>
                        <button
                          type="button"
                          onClick={() =>
                            handleAction(res.id, () => activateReservation(res.id))
                          }
                          disabled={isBusy}
                        >
                          Activate
                        </button>
                        <button
                          type="button"
                          onClick={() =>
                            handleAction(res.id, () => expireReservation(res.id))
                          }
                          disabled={isBusy}
                        >
                          Expire
                        </button>
                        <button
                          type="button"
                          onClick={() => handleStartEdit(res)}
                          disabled={isBusy}
                        >
                          Edit
                        </button>
                        <button
                          type="button"
                          className="btn-danger"
                          onClick={() =>
                            handleAction(res.id, () => cancelReservation(res.id))
                          }
                          disabled={isBusy}
                        >
                          Cancel Reservation
                        </button>
                      </>
                    )}

                    {res.status === 'ACTIVE' && (
                      <>
                        <button
                          type="button"
                          onClick={() =>
                            handleAction(res.id, () => completeReservation(res.id))
                          }
                          disabled={isBusy}
                        >
                          Complete
                        </button>
                        <button
                          type="button"
                          className="btn-danger"
                          onClick={() =>
                            handleAction(res.id, () => cancelReservation(res.id))
                          }
                          disabled={isBusy}
                        >
                          Cancel Reservation
                        </button>
                      </>
                    )}
                  </div>
                )}
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}

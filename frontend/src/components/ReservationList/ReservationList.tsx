import { useCallback, useEffect, useState } from 'react'
import {
  activateReservation,
  cancelReservation,
  completeReservation,
  expireReservation,
  getReservation,
  listRoomReservations,
  updateReservationMetadata,
} from '../../API/reservations'
import { formatApiError } from '../../API/client'
import { useAdminMode } from '../../auth/useAdminMode'
import { formatDateTime, formatDateTimeRange } from '../../utils/date'
import type { Room } from '../../types/room'
import type { Reservation } from '../../types/reservation'
import './ReservationList.css'
import { reservationStatusLabels } from '../../utils/labels'
import { reservationMetadataSchema } from './reservationMetadataSchema'

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
  const { permissions } = useAdminMode()
  const [reservations, setReservations] = useState<Reservation[]>([])
  const [foreignDetails, setForeignDetails] = useState<Record<string, Reservation>>({})
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [editAttendees, setEditAttendees] = useState<string>('1')
  const [editNote, setEditNote] = useState<string>('')
  const [editReservedFor, setEditReservedFor] = useState<string>('')
  const [actionInProgress, setActionInProgress] = useState<string | null>(null)

  const reload = useCallback(async () => {
    try {
      const data = await listRoomReservations(room.id)
      setReservations(data)
      setForeignDetails({})
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
    setEditAttendees(res.expectedAttendees == null ? '' : String(res.expectedAttendees))
    setEditNote(res.note ?? '')
    setEditReservedFor(res.reservedFor ?? '')
    setError(null)
  }

  function handleCancelEdit() {
    setEditingId(null)
    setError(null)
  }

  async function handleSaveEdit(res: Reservation) {
    const maxCapacity = res.seatingArrangement?.maxCapacity
    const parsed = reservationMetadataSchema(maxCapacity).safeParse({
      expectedAttendees: editAttendees,
      reservedFor: editReservedFor,
      note: editNote,
    })
    if (!parsed.success) {
      setError(parsed.error.issues[0]?.message ?? 'Die Eingaben sind ungültig.')
      return
    }

    setActionInProgress(res.id)
    setError(null)
    try {
      await updateReservationMetadata(res.id, {
        expectedAttendees: parsed.data.expectedAttendees,
        note: parsed.data.note.trim() || undefined,
        reservedFor: parsed.data.reservedFor,
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
    return <p className="status-loading">Reservierungen werden geladen…</p>
  }

  return (
    <div className="reservation-list-container">
      {error && (
        <p role="alert" className="feedback-error">
          {error}
        </p>
      )}

      {reservations.length === 0 ? (
        <p className="status-empty">Für diesen Raum gibt es keine Reservierungen.</p>
      ) : (
        <ul className="list-plain reservation-list">
          {reservations.map((res) => {
            const visible = foreignDetails[res.id] ?? res
            const isEditing = editingId === res.id
            const isBusy = actionInProgress === res.id
            const canManage = res.ownedByMe ? permissions.includes('OWN_RESERVATION_MANAGE')
              : permissions.includes('OTHER_RESERVATION_MANAGE')
            const hasDetails = visible.seatingArrangement !== null

            return (
              <li key={res.id} className="panel reservation-card">
                <div className="reservation-header">
                  <span className={`status-badge status-${res.status.toLowerCase()}`}>
                    {reservationStatusLabels[res.status]}
                  </span>
                  <span className="reservation-time">
                    {formatDateTimeRange(res.startTime, res.endTime)}
                  </span>
                </div>

                {!hasDetails && <p className="reservation-occupied">Belegt</p>}

                {!res.ownedByMe && permissions.includes('OTHER_RESERVATION_MANAGE') && !foreignDetails[res.id] &&
                  <button type="button" onClick={() => { void getReservation(res.id).then(detail =>
                    setForeignDetails(current => ({ ...current, [res.id]: detail }))).catch(err => setError(formatApiError(err))) }}>
                    Details anzeigen
                  </button>}
                {hasDetails && <div className="reservation-details">
                  <p>
                    <strong>Sitzordnung:</strong> {visible.seatingArrangement?.name} (Max:{' '}
                    {visible.seatingArrangement?.maxCapacity})
                  </p>
                  <p>
                    <strong>Attendees:</strong> {visible.expectedAttendees}
                  </p>
                  <p>
                    <strong>Reserviert für:</strong> {visible.reservedFor}
                  </p>
                  <p>
                    <strong>Gebucht von:</strong> {visible.createdBy}
                  </p>
                  <p className="reservation-created-at">
                    <small>Erstellt: {visible.createdAt ? formatDateTime(visible.createdAt) : ''}</small>
                  </p>
                  {visible.additionalEquipment && visible.additionalEquipment.length > 0 && (
                    <p>
                      <strong>Zusätzliche Ausstattung:</strong>{' '}
                      {visible.additionalEquipment.map((eq) => eq.name).join(', ')}
                    </p>
                  )}
                  {visible.note && (
                    <p className="reservation-note">
                      <strong>Notes:</strong> {visible.note}
                    </p>
                  )}
                </div>}

                {hasDetails && canManage && (isEditing ? (
                  <form
                    className="reservation-edit-form"
                    noValidate
                    onSubmit={(e) => {
                      e.preventDefault()
                      handleSaveEdit(visible)
                    }}
                  >
                    <div>
                      <label htmlFor={`edit-attendees-${res.id}`}>Teilnehmende bearbeiten</label>
                      <input
                        id={`edit-attendees-${res.id}`}
                        type="number"
                        min="1"
                        max={visible.seatingArrangement?.maxCapacity}
                        required
                        value={editAttendees}
                        onChange={(e) =>
                          setEditAttendees(
                          e.target.value,
                        )
                        }
                      />
                    </div>
                    <div>
                      <label htmlFor={`edit-reserved-for-${res.id}`}>Reserviert für</label>
                      <input
                        id={`edit-reserved-for-${res.id}`}
                        type="text"
                        required
                        maxLength={255}
                        value={editReservedFor}
                        onChange={(e) => setEditReservedFor(e.target.value)}
                      />
                    </div>
                    <div>
                      <label htmlFor={`edit-notes-${res.id}`}>Notizen bearbeiten</label>
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
                        Speichern
                      </button>
                      <button type="button" onClick={handleCancelEdit} disabled={isBusy}>
                        Abbrechen
                      </button>
                    </div>
                  </form>
                ) : (
                  <div className="actions reservation-actions">
                    {visible.status === 'RESERVED' && (
                      <>
                        <button
                          type="button"
                          onClick={() =>
                            handleAction(res.id, () => activateReservation(res.id))
                          }
                          disabled={isBusy}
                        >
                          Einchecken
                        </button>
                        <button
                          type="button"
                          onClick={() =>
                            handleAction(res.id, () => expireReservation(res.id))
                          }
                          disabled={isBusy}
                        >
                          Verfallen lassen
                        </button>
                        <button
                          type="button"
                          onClick={() => handleStartEdit(visible)}
                          disabled={isBusy}
                        >
                          Bearbeiten
                        </button>
                        <button
                          type="button"
                          className="btn-danger"
                          onClick={() =>
                            handleAction(res.id, () => cancelReservation(res.id))
                          }
                          disabled={isBusy}
                        >
                          Stornieren
                        </button>
                      </>
                    )}

                    {visible.status === 'ACTIVE' && (
                      <>
                        <button
                          type="button"
                          onClick={() =>
                            handleAction(res.id, () => completeReservation(res.id))
                          }
                          disabled={isBusy}
                        >
                          Abschließen
                        </button>
                        <button
                          type="button"
                          className="btn-danger"
                          onClick={() =>
                            handleAction(res.id, () => cancelReservation(res.id))
                          }
                          disabled={isBusy}
                        >
                          Stornieren
                        </button>
                      </>
                    )}
                  </div>
                ))}
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}

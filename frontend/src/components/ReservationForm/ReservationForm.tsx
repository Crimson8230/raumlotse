import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { createReservation, getAvailableEquipment } from '../../API/reservations'
import { ApiError, formatApiError } from '../../API/client'
import type { Room } from '../../types/room'
import type { EquipmentTypeSummary, Reservation } from '../../types/reservation'
import './ReservationForm.css'

export interface ReservationFormProps {
  room: Room
  onSaved: (reservation: Reservation) => void
  onCancel?: () => void
}

export function ReservationForm({ room, onSaved, onCancel }: ReservationFormProps) {
  const [startTime, setStartTime] = useState('')
  const [endTime, setEndTime] = useState('')
  const [seatingArrangementId, setSeatingArrangementId] = useState(
    room.seatingArrangements.length === 1 ? room.seatingArrangements[0].id : '',
  )
  const [expectedAttendees, setExpectedAttendees] = useState<number | ''>('')
  const [createdBy, setCreatedBy] = useState('')
  const [note, setNote] = useState('')
  const [availableEquipment, setAvailableEquipment] = useState<EquipmentTypeSummary[]>([])
  const [selectedEquipmentIds, setSelectedEquipmentIds] = useState<string[]>([])
  const [loadingEquipment, setLoadingEquipment] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [isConflict, setIsConflict] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    let ignore = false
    getAvailableEquipment(room.id)
      .then((eq) => {
        if (!ignore) {
          setAvailableEquipment(eq)
          setLoadingEquipment(false)
        }
      })
      .catch(() => {
        if (!ignore) {
          setLoadingEquipment(false)
        }
      })
    return () => {
      ignore = true
    }
  }, [room.id])

  function toggleEquipment(id: string) {
    setSelectedEquipmentIds((prev) =>
      prev.includes(id) ? prev.filter((item) => item !== id) : [...prev, id],
    )
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError(null)
    setIsConflict(false)

    const selectedArrangement = room.seatingArrangements.find(
      (sa) => sa.id === seatingArrangementId,
    )
    const attendeesNum = Number(expectedAttendees)
    if (selectedArrangement && attendeesNum > selectedArrangement.maxCapacity) {
      setError(
        `Expected attendees (${attendeesNum}) cannot exceed arrangement capacity (${selectedArrangement.maxCapacity}).`,
      )
      return
    }

    if (!startTime || !endTime) {
      setError('Start time and end time are required.')
      return
    }

    if (!seatingArrangementId) {
      setError('Please select a seating arrangement.')
      return
    }

    if (!selectedArrangement) {
      setError('Invalid seating arrangement selected.')
      return
    }

    if (!expectedAttendees || isNaN(attendeesNum) || attendeesNum < 1) {
      setError('Expected attendees must be a positive integer greater than or equal to 1.')
      return
    }

    if (!createdBy.trim()) {
      setError('Booked by is required.')
      return
    }

    const startIso =
      startTime.includes('Z') || startTime.includes('+')
        ? startTime
        : new Date(startTime).toISOString()
    const endIso =
      endTime.includes('Z') || endTime.includes('+')
        ? endTime
        : new Date(endTime).toISOString()

    setSubmitting(true)
    try {
      const saved = await createReservation(room.id, {
        startTime: startIso,
        endTime: endIso,
        seatingArrangementId,
        expectedAttendees: attendeesNum,
        createdBy: createdBy.trim(),
        note: note.trim() || undefined,
        additionalEquipmentTypeIds:
          selectedEquipmentIds.length > 0 ? selectedEquipmentIds : undefined,
      })
      onSaved(saved)
    } catch (err) {
      setError(formatApiError(err))
      if (err instanceof ApiError && err.status === 409) {
        setIsConflict(true)
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="panel reservation-form">
      {error && (
        <div role="alert" className={isConflict ? 'feedback-conflict' : 'feedback-error'}>
          <p>{error}</p>
          {isConflict && (
            <p>
              <Link to="/rooms">Zurück zur Raumübersicht</Link>
            </p>
          )}
        </div>
      )}

      <div>
        <label htmlFor="res-start-time">Start Time</label>
        <input
          id="res-start-time"
          type="datetime-local"
          lang="de-AT"
          required
          value={startTime}
          onChange={(e) => setStartTime(e.target.value)}
        />
      </div>

      <div>
        <label htmlFor="res-end-time">End Time</label>
        <input
          id="res-end-time"
          type="datetime-local"
          lang="de-AT"
          required
          value={endTime}
          onChange={(e) => setEndTime(e.target.value)}
        />
      </div>

      <div>
        <label htmlFor="res-seating">Seating Arrangement</label>
        <select
          id="res-seating"
          required
          value={seatingArrangementId}
          onChange={(e) => setSeatingArrangementId(e.target.value)}
        >
          {room.seatingArrangements.length > 1 && (
            <option value="">Select seating arrangement</option>
          )}
          {room.seatingArrangements.map((sa) => (
            <option key={sa.id} value={sa.id}>
              {sa.name} (Capacity: {sa.maxCapacity})
            </option>
          ))}
        </select>
      </div>

      <div>
        <label htmlFor="res-attendees">Attendees</label>
        <input
          id="res-attendees"
          type="number"
          min="1"
          required
          value={expectedAttendees}
          onChange={(e) =>
            setExpectedAttendees(e.target.value === '' ? '' : parseInt(e.target.value, 10))
          }
        />
      </div>

      <div>
        <label htmlFor="res-booked-by">Booked By</label>
        <input
          id="res-booked-by"
          type="text"
          required
          maxLength={255}
          value={createdBy}
          onChange={(e) => setCreatedBy(e.target.value)}
        />
      </div>

      <div>
        <label htmlFor="res-notes">Notes</label>
        <textarea
          id="res-notes"
          rows={3}
          maxLength={2000}
          value={note}
          onChange={(e) => setNote(e.target.value)}
        />
      </div>

      <fieldset>
        <legend>Additional Equipment</legend>
        {loadingEquipment ? (
          <p>Loading available equipment...</p>
        ) : availableEquipment.length === 0 ? (
          <p className="equipment-empty-notice">
            All catalog equipment is already present in this room
          </p>
        ) : (
          <div className="reservation-equipment-list">
            {availableEquipment.map((eq) => (
              <label key={eq.id} className="reservation-equipment-item">
                <input
                  type="checkbox"
                  checked={selectedEquipmentIds.includes(eq.id)}
                  onChange={() => toggleEquipment(eq.id)}
                />
                <span>{eq.name}</span>
              </label>
            ))}
          </div>
        )}
      </fieldset>

      <div className="actions">
        <button type="submit" disabled={submitting}>
          Confirm Reservation
        </button>
        {onCancel && (
          <button type="button" onClick={onCancel} disabled={submitting}>
            Cancel
          </button>
        )}
      </div>
    </form>
  )
}

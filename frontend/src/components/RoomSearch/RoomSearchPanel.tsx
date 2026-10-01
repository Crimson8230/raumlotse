import { useState } from 'react'
import type { FormEvent } from 'react'
import { validate } from './roomSearchParams'
import type { Building, EquipmentType, RoomSearchFormState } from '../../types/room'
import './RoomSearchPanel.css'

export interface RoomSearchPanelProps {
  /** Initial filters; remount the panel (e.g. via `key`) to load different ones. */
  value: RoomSearchFormState
  buildings: Building[]
  seatingArrangementNames: string[]
  /** Equipment types offered as filters; the caller passes only active ones. */
  equipmentTypes: EquipmentType[]
  onSearch: (state: RoomSearchFormState) => void
  onReset: () => void
  disabled?: boolean
}

export function RoomSearchPanel({
  value,
  buildings,
  seatingArrangementNames,
  equipmentTypes,
  onSearch,
  onReset,
  disabled = false,
}: RoomSearchPanelProps) {
  const [draft, setDraft] = useState<RoomSearchFormState>(value)
  const [errors, setErrors] = useState<ReturnType<typeof validate>>({})

  function update<K extends keyof RoomSearchFormState>(field: K, fieldValue: RoomSearchFormState[K]) {
    setDraft((prev) => ({ ...prev, [field]: fieldValue }))
  }

  function toggleEquipment(id: string) {
    setDraft((prev) => ({
      ...prev,
      equipmentTypeIds: prev.equipmentTypeIds.includes(id)
        ? prev.equipmentTypeIds.filter((existing) => existing !== id)
        : [...prev.equipmentTypeIds, id],
    }))
  }

  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    const found = validate(draft)
    setErrors(found)
    if (Object.keys(found).length === 0) onSearch(draft)
  }

  return (
    <form className="panel room-search-panel" onSubmit={handleSubmit} noValidate aria-label="Raumsuche">
      {disabled && <p className="room-search-hint">Die Suche umfasst nur aktive Räume.</p>}
      <fieldset disabled={disabled} className="room-search-fields">
        <div>
          <label htmlFor="search-min-persons">Personen min.</label>
          <input
            id="search-min-persons"
            type="number"
            min={1}
            step={1}
            inputMode="numeric"
            value={draft.minPersons}
            onChange={(e) => update('minPersons', e.target.value)}
            aria-invalid={errors.minPersons ? true : undefined}
            aria-describedby={errors.minPersons ? 'search-min-persons-error' : undefined}
          />
          {errors.minPersons && (
            <p id="search-min-persons-error" className="feedback-error">
              {errors.minPersons}
            </p>
          )}
        </div>

        <div>
          <label htmlFor="search-max-persons">Personen max.</label>
          <input
            id="search-max-persons"
            type="number"
            min={1}
            step={1}
            inputMode="numeric"
            value={draft.maxPersons}
            onChange={(e) => update('maxPersons', e.target.value)}
            aria-invalid={errors.maxPersons ? true : undefined}
            aria-describedby={errors.maxPersons ? 'search-max-persons-error' : undefined}
          />
          {errors.maxPersons && (
            <p id="search-max-persons-error" className="feedback-error">
              {errors.maxPersons}
            </p>
          )}
        </div>

        <div>
          <label htmlFor="search-building">Gebäude</label>
          <select id="search-building" value={draft.buildingId} onChange={(e) => update('buildingId', e.target.value)}>
            <option value="">Alle Gebäude</option>
            {buildings.map((building) => (
              <option key={building.id} value={building.id}>
                {building.name}
              </option>
            ))}
          </select>
        </div>

        <div>
          <label htmlFor="search-seating-arrangement">Bestuhlung</label>
          <select
            id="search-seating-arrangement"
            value={draft.seatingArrangement}
            onChange={(e) => update('seatingArrangement', e.target.value)}
          >
            <option value="">Alle</option>
            {seatingArrangementNames.map((name) => (
              <option key={name} value={name}>
                {name}
              </option>
            ))}
          </select>
        </div>

        {equipmentTypes.length > 0 && (
          <fieldset className="room-search-equipment">
            <legend>Ausstattung</legend>
            {equipmentTypes.map((type) => (
              <label key={type.id} className="checkbox-label">
                <input
                  type="checkbox"
                  checked={draft.equipmentTypeIds.includes(type.id)}
                  onChange={() => toggleEquipment(type.id)}
                />
                {type.name}
              </label>
            ))}
          </fieldset>
        )}

        <div>
          <label htmlFor="search-date">Datum</label>
          <input
            id="search-date"
            type="date"
            value={draft.date}
            onChange={(e) => update('date', e.target.value)}
            aria-invalid={errors.date ? true : undefined}
            aria-describedby={errors.date ? 'search-date-error' : undefined}
          />
          {errors.date && (
            <p id="search-date-error" className="feedback-error">
              {errors.date}
            </p>
          )}
        </div>

        <div>
          <label htmlFor="search-start-time">Von</label>
          <input
            id="search-start-time"
            type="time"
            value={draft.startTime}
            onChange={(e) => update('startTime', e.target.value)}
          />
        </div>

        <div>
          <label htmlFor="search-end-time">Bis</label>
          <input
            id="search-end-time"
            type="time"
            value={draft.endTime}
            onChange={(e) => update('endTime', e.target.value)}
            aria-invalid={errors.endTime ? true : undefined}
            aria-describedby={errors.endTime ? 'search-end-time-error' : undefined}
          />
          {errors.endTime && (
            <p id="search-end-time-error" className="feedback-error">
              {errors.endTime}
            </p>
          )}
        </div>

        <label className="checkbox-label">
          <input
            type="checkbox"
            checked={draft.barrierFree}
            onChange={(e) => update('barrierFree', e.target.checked)}
          />
          Barrierefrei erreichbar
        </label>

        <div className="actions room-search-actions">
          <button type="submit">Suchen</button>
          <button type="button" onClick={onReset}>
            Filter zurücksetzen
          </button>
        </div>
      </fieldset>
    </form>
  )
}

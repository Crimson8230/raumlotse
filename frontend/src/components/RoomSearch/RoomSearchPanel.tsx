import { useState } from 'react'
import type { FormEvent } from 'react'
import { Field } from '../ui'
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
        <Field label="Personen min." error={errors.minPersons}>
          {(control) => (
            <input
              {...control}
              type="number"
              min={1}
              step={1}
              inputMode="numeric"
              value={draft.minPersons}
              onChange={(e) => update('minPersons', e.target.value)}
            />
          )}
        </Field>

        <Field label="Personen max." error={errors.maxPersons}>
          {(control) => (
            <input
              {...control}
              type="number"
              min={1}
              step={1}
              inputMode="numeric"
              value={draft.maxPersons}
              onChange={(e) => update('maxPersons', e.target.value)}
            />
          )}
        </Field>

        <Field label="Gebäude">
          {(control) => (
            <select {...control} value={draft.buildingId} onChange={(e) => update('buildingId', e.target.value)}>
              <option value="">Alle Gebäude</option>
              {buildings.map((building) => (
                <option key={building.id} value={building.id}>
                  {building.name}
                </option>
              ))}
            </select>
          )}
        </Field>

        <Field label="Bestuhlung">
          {(control) => (
            <select
              {...control}
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
          )}
        </Field>

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

        <Field label="Datum" error={errors.date}>
          {(control) => (
            <input
              {...control}
              type="date"
              value={draft.date}
              onChange={(e) => update('date', e.target.value)}
            />
          )}
        </Field>

        <Field label="Von">
          {(control) => (
            <input
              {...control}
              type="time"
              value={draft.startTime}
              onChange={(e) => update('startTime', e.target.value)}
            />
          )}
        </Field>

        <Field label="Bis" error={errors.endTime}>
          {(control) => (
            <input
              {...control}
              type="time"
              value={draft.endTime}
              onChange={(e) => update('endTime', e.target.value)}
            />
          )}
        </Field>

        <label className="checkbox-label">
          <input
            type="checkbox"
            checked={draft.barrierFree}
            onChange={(e) => update('barrierFree', e.target.checked)}
          />
          Barrierefrei erreichbar
        </label>

        <label className="checkbox-label">
          <input
            type="checkbox"
            checked={draft.favoritesOnly}
            onChange={(e) => update('favoritesOnly', e.target.checked)}
          />
          Nur Favoriten
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

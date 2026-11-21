import { useState, type FormEvent } from 'react'
import type { Position } from '../../types/map'
import type { MapNetwork, ReferenceSegmentInput } from '../../types/pathNetwork'
import { Field } from '../ui'

interface Props {
  network: MapNetwork
  /** The reference tool is selected: clicks on the map set the endpoints. */
  active: boolean
  draft: Position[]
  canEdit: boolean
  onSave: (input: ReferenceSegmentInput) => void
  onDelete: () => void
  onResetDraft: () => void
}

const meters = new Intl.NumberFormat('de-DE', { maximumFractionDigits: 2 })

function parseLength(text: string): number | undefined {
  const value = Number(text.trim().replace(',', '.'))
  return text.trim() !== '' && Number.isFinite(value) && value > 0 ? value : undefined
}

function LengthForm({ label, submitLabel, initial, onSubmit, onCancel }: {
  label: string
  submitLabel: string
  initial: string
  onSubmit: (length: number) => void
  onCancel?: () => void
}) {
  const [text, setText] = useState(initial)
  const [error, setError] = useState<string>()

  function submit(event: FormEvent) {
    event.preventDefault()
    const length = parseLength(text)
    if (length === undefined) {
      setError('Bitte eine Länge größer als 0 eingeben.')
      return
    }
    setError(undefined)
    onSubmit(length)
  }

  return (
    <form onSubmit={submit}>
      <Field label={label} error={error}>
        {(control) => (
          <input {...control} inputMode="decimal" value={text} onChange={(event) => setText(event.target.value)} />
        )}
      </Field>
      <button type="submit" className="btn-primary">{submitLabel}</button>
      {onCancel && <button type="button" onClick={onCancel}>Abbrechen</button>}
    </form>
  )
}

/** Sets the scale of a map by a reference segment of known length (the scale is derived from it). */
export function ReferenceSegmentTool({ network, active, draft, canEdit, onSave, onDelete, onResetDraft }: Props) {
  const reference = network.referenceSegment
  return (
    <section aria-label="Maßstab" className="path-reference-tool">
      <h2>Maßstab</h2>
      {!network.scale.available && (
        <p>Maßstab fehlt. Streckenlängen sind nicht verfügbar, bis eine Referenzstrecke festgelegt ist.</p>
      )}
      {reference && (
        <p>
          Referenzstrecke: {meters.format(reference.lengthMeters)} m ({meters.format(reference.pixelsPerMeter)} Pixel pro Meter)
        </p>
      )}
      {canEdit && reference && !(active && draft.length === 2) && (
        <>
          <LengthForm
            key={`${reference.lengthMeters}`}
            label="Länge der Referenzstrecke in Metern"
            submitLabel="Länge speichern"
            initial={String(reference.lengthMeters).replace('.', ',')}
            onSubmit={(lengthMeters) =>
              onSave({ x1: reference.x1, y1: reference.y1, x2: reference.x2, y2: reference.y2, lengthMeters })
            }
          />
          <button type="button" onClick={onDelete}>Referenzstrecke löschen</button>
        </>
      )}
      {canEdit && active && draft.length < 2 && (
        <p>Klicken Sie nacheinander zwei Punkte der Karte an, deren Abstand Sie kennen ({draft.length} von 2 gesetzt).</p>
      )}
      {canEdit && active && draft.length === 2 && (
        <LengthForm
          key="draft"
          label="Reale Länge in Metern"
          submitLabel="Referenzstrecke speichern"
          initial=""
          onCancel={onResetDraft}
          onSubmit={(lengthMeters) =>
            onSave({ x1: draft[0].x, y1: draft[0].y, x2: draft[1].x, y2: draft[1].y, lengthMeters })
          }
        />
      )}
    </section>
  )
}

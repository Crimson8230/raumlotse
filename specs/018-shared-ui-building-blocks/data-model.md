# Data Model: Shared UI Building Blocks

No persisted data. These are the TypeScript shapes shared by the blocks (frontend only).

## State vocabulary (`vocabulary.ts`)

```ts
type OccupancyState = 'frei' | 'belegt' | 'meine' | 'jetzt' | 'gesperrt'

interface StateDefinition {
  state: OccupancyState
  word: string          // 'frei' | 'belegt' | 'meine' | 'jetzt' | 'gesperrt' (display + spoken)
  pattern: 'empty' | 'hatch' | 'solid' | 'line' | 'dotted'
  tone: 'free' | 'busy' | 'ink' | 'primary' | 'muted'   // maps to existing tokens
}
```

Rules: five fixed entries; `jetzt` is a marker state (a point in time), the others are window states. Priority when windows overlap: `gesperrt` > `belegt` > `meine` > `frei`.

## Time window (strip input)

| Field | Type | Rule |
|---|---|---|
| start | `Date \| string` (ISO) | within or before range; clamped |
| end | `Date \| string` (ISO) | must be ≥ start; zero-length windows ignored |
| state | `'belegt' \| 'meine' \| 'gesperrt'` | `frei` is implicit (gaps) |
| label | `string?` | optional, appended to the spoken window, e.g. event title (later features) |

Strip props also take `rangeStart`, `rangeEnd` (required), `now?` (defaults to current time; marker hidden when outside range), `name?` (prefix of the accessible name, e.g. room name).

## Field message

| Field | Type | Rule |
|---|---|---|
| kind | `'hint' \| 'error'` | error is announced first |
| text | `string` | German user-facing text |

## Wayfinding hint

| Field | Type | Rule |
|---|---|---|
| direction | `'geradeaus' \| 'links' \| 'rechts' \| 'hoch' \| 'runter'` | spoken as word |
| target | `string` | e.g. "Aufzug", "Raum 2.14", "Etage 2" |
| distance | `string?` | pre-formatted, e.g. "ca. 40 m" or "1 Min." |

## Field-label mapping

`Record<string, string>` – technical field name → German label; `fieldLabel(name, overrides?)` returns override ?? default ?? humanized name.

## Relationships

`Strip`, `Chip` read the vocabulary; `Field` contains `ErrorMessage`; `DataList`/`DataTable` accept arbitrary cells (may contain `Chip`, `Strip`, `Wayfinding`).

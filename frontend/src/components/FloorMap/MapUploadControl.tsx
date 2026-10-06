import { useEffect, useState, type FormEvent } from 'react'
import { listBuildings } from '../../API/buildings'
import { listFloors } from '../../API/floors'
import { formatApiError } from '../../API/client'
import { uploadMapImage } from '../../API/maps'
import type { MapSummary } from '../../types/map'
import type { Building, Floor } from '../../types/room'

interface Props {
  /** When given, the control replaces this map's image instead of creating a new map. */
  map?: MapSummary
  existingMaps: MapSummary[]
  onSaved: (map: MapSummary) => void
}

export function MapUploadControl({ map, existingMaps, onSaved }: Props) {
  const [buildings, setBuildings] = useState<Building[]>([])
  const [floors, setFloors] = useState<Floor[]>([])
  const [buildingId, setBuildingId] = useState('')
  const [floorId, setFloorId] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string>()
  const [notice, setNotice] = useState<string>()
  const replacing = map !== undefined

  useEffect(() => {
    if (replacing) return
    let active = true
    listBuildings('active')
      .then((result) => active && setBuildings(result))
      .catch((err: unknown) => active && setError(formatApiError(err)))
    return () => {
      active = false
    }
  }, [replacing])

  useEffect(() => {
    if (replacing || !buildingId) return
    let active = true
    listFloors(buildingId, 'active')
      .then((result) => active && setFloors(result))
      .catch((err: unknown) => active && setError(formatApiError(err)))
    return () => {
      active = false
    }
  }, [replacing, buildingId])

  const mappedFloorIds = new Set(existingMaps.map((existing) => existing.floorId))
  const freeFloors = floors.filter((floor) => !mappedFloorIds.has(floor.id))
  const targetFloorId = replacing ? map.floorId : floorId

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!file || !targetFloorId) return
    setBusy(true)
    setError(undefined)
    setNotice(undefined)
    try {
      const saved = await uploadMapImage(targetFloorId, file)
      if (saved.aspectRatioChanged) {
        setNotice('Das Seitenverhältnis des Bildes hat sich geändert; vorhandene Positionen passen eventuell nicht mehr zum Grundriss.')
      }
      setFile(null)
      onSaved(saved)
    } catch (err) {
      setError(formatApiError(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="map-upload" onSubmit={submit}>
      {!replacing && (
        <>
          <label>
            Gebäude
            <select
              value={buildingId}
              onChange={(event) => {
                setBuildingId(event.target.value)
                setFloorId('')
                setFloors([])
              }}
            >
              <option value="">Bitte wählen</option>
              {buildings.map((building) => (
                <option key={building.id} value={building.id}>
                  {building.name}
                </option>
              ))}
            </select>
          </label>
          <label>
            Stockwerk
            <select value={floorId} onChange={(event) => setFloorId(event.target.value)} disabled={!buildingId}>
              <option value="">Bitte wählen</option>
              {freeFloors.map((floor) => (
                <option key={floor.id} value={floor.id}>
                  {floor.name}
                </option>
              ))}
            </select>
          </label>
        </>
      )}
      <label>
        Grundriss (PNG oder JPEG, max. 10 MB)
        <input
          type="file"
          accept="image/png,image/jpeg"
          onChange={(event) => setFile(event.target.files?.[0] ?? null)}
        />
      </label>
      <button type="submit" disabled={busy || !file || !targetFloorId}>
        {replacing ? 'Bild ersetzen' : 'Karte anlegen'}
      </button>
      {error && <p role="alert">{error}</p>}
      {notice && <p role="status">{notice}</p>}
    </form>
  )
}

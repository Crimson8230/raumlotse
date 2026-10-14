import { useCallback, useEffect, useRef, useState } from 'react'
import { getOccupancyForecast, getOccupancySnapshot } from '../API/adminOccupancy'
import { listBuildings } from '../API/buildings'
import { ApiError, formatApiError } from '../API/client'
import type { Building } from '../types/room'
import type { BookingEntry, BuildingOccupancy, HourForecast, OccupancyForecast, OccupancySnapshot } from '../types/adminOccupancy'
import './AdminStatisticsPage.css'
import './AdminOccupancyPage.css'
import { DataList, DataTable, ErrorMessage, Field, RoomName, TimeText } from '../components/ui'

const REFRESH_INTERVAL_MS = 30_000

function formatStamp(iso: string): string {
  return new Date(iso).toLocaleString('de-DE', { dateStyle: 'short', timeStyle: 'medium' })
}

function formatTime(iso: string): string {
  return new Date(iso).toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit', timeZone: 'Europe/Berlin' })
}

function todayLocal(): string {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
}

function persons(count: number): string {
  return count === 1 ? '1 Person' : `${count} Personen`
}

function checkInText(booking: BookingEntry, notCheckedIn: string): string {
  return booking.checkInStatus === 'CHECKED_IN'
    ? `eingecheckt${booking.checkedInAt ? ` seit ${formatTime(booking.checkedInAt)}` : ''}`
    : notCheckedIn
}

function timeRange(booking: BookingEntry): string {
  return `${formatTime(booking.startTime)}–${formatTime(booking.endTime)}`
}

/** Zeilentext bleibt Wort für Wort gleich; Raumname in Serif, Zeiten und Zahlen in Mono (Feature 020). */
function bookingLine(booking: BookingEntry) {
  return (
    <>
      <RoomName>{booking.roomName}</RoomName> · <TimeText>{timeRange(booking)}</TimeText> ·{' '}
      <TimeText>{persons(booking.expectedAttendees)}</TimeText> · {checkInText(booking, 'reserviert')}
    </>
  )
}

function runningLine(booking: BookingEntry) {
  return (
    <>
      {booking.buildingName} · {booking.floorName} · <RoomName>{booking.roomName}</RoomName> ·{' '}
      <TimeText>{timeRange(booking)}</TimeText> · <TimeText>{persons(booking.expectedAttendees)}</TimeText> ·{' '}
      {checkInText(booking, 'noch nicht eingecheckt')}
    </>
  )
}

function PlannedValue({ value, peak }: { value: number | null; peak: boolean }) {
  return <>{value}{peak && <span className="occupancy-peak">Spitze</span>}</>
}

/** Where people are expected in one hour, as a popup over the forecast; closes with the button, Escape or the backdrop. */
function HourDetailDialog({ hour, onClose, onBackToCurrent }: {
  hour: HourForecast; onClose: () => void; onBackToCurrent: () => void
}) {
  const closeButton = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    closeButton.current?.focus()
    const onKeyDown = (event: KeyboardEvent) => { if (event.key === 'Escape') onClose() }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [onClose])

  return (
    <div className="occupancy-dialog-backdrop" data-testid="occupancy-dialog-backdrop" onClick={onClose}>
      <div className="occupancy-dialog" role="dialog" aria-modal="true" aria-labelledby="occupancy-dialog-title"
        onClick={event => event.stopPropagation()}>
        <div className="occupancy-dialog-header">
          <h2 id="occupancy-dialog-title">Wo werden um {hour.localLabel} Personen erwartet?</h2>
          <button type="button" ref={closeButton} onClick={onClose}>Schließen</button>
        </div>
        {hour.floors.length === 0 && <p className="statistics-status">In dieser Stunde sind keine Räume gebucht.</p>}
        {hour.floors.map(group => (
          <div key={group.floorId} className="occupancy-floor-group">
            <h3>{group.buildingName} · {group.floorName} – {persons(group.expectedPersons)}</h3>
            <DataList
              aria-label={`Buchungen: ${group.buildingName} · ${group.floorName}`}
              items={group.bookings}
              itemKey={booking => booking.reservationId}
              renderItem={bookingLine}
              emptyText="Keine Buchungen."
            />
          </div>
        ))}
        <button type="button" onClick={onBackToCurrent}>Zur aktuellen Situation</button>
      </div>
    </div>
  )
}

function isAccessDenied(reason: unknown): boolean {
  return reason instanceof ApiError && (reason.status === 401 || reason.status === 403)
}

export default function AdminOccupancyPage() {
  const [snapshot, setSnapshot] = useState<OccupancySnapshot | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [stale, setStale] = useState(false)
  const [accessDenied, setAccessDenied] = useState(false)
  const [loading, setLoading] = useState(true)
  const [day, setDay] = useState(todayLocal)
  const [forecast, setForecast] = useState<OccupancyForecast | null>(null)
  const [forecastError, setForecastError] = useState<string | null>(null)
  const [selectedHour, setSelectedHour] = useState<HourForecast | null>(null)
  const hourTrigger = useRef<HTMLButtonElement | null>(null)
  const [buildingOptions, setBuildingOptions] = useState<Building[]>([])
  const [buildingId, setBuildingId] = useState('')

  useEffect(() => {
    let active = true
    // The filter is optional; without the list only "Alle Gebäude" is offered.
    void listBuildings('active').then((loaded) => { if (active) setBuildingOptions(loaded) }).catch(() => undefined)
    return () => { active = false }
  }, [])

  useEffect(() => {
    let active = true
    // Polling continues while access is denied, so the page recovers once the right is granted again.
    function load() {
      void getOccupancySnapshot(buildingId || undefined).then((loaded) => {
        if (!active) return
        setSnapshot(loaded)
        setStale(false)
        setError(null)
        setAccessDenied(false)
      }).catch((reason: unknown) => {
        if (!active) return
        if (isAccessDenied(reason)) {
          // FR-012: no stale occupancy data once the right is gone.
          setAccessDenied(true)
          setSnapshot(null)
          setForecast(null)
          setSelectedHour(null)
          setStale(false)
          setError('Der Zugriff auf die Belegungsübersicht ist nicht mehr erlaubt.')
          return
        }
        setSnapshot((previous) => {
          if (previous) setStale(true)
          else setError(formatApiError(reason))
          return previous
        })
      }).finally(() => {
        if (active) setLoading(false)
      })
    }
    load()
    const timer = window.setInterval(load, REFRESH_INTERVAL_MS)
    return () => {
      active = false
      window.clearInterval(timer)
    }
  }, [buildingId])

  useEffect(() => {
    if (!day || accessDenied) return
    let active = true
    void getOccupancyForecast(day, buildingId || undefined).then((loaded) => {
      if (!active) return
      setForecast(loaded)
      setForecastError(null)
    }).catch((reason: unknown) => {
      if (!active) return
      setForecast(null)
      setForecastError(formatApiError(reason))
    })
    return () => { active = false }
  }, [day, buildingId, accessDenied])

  const closeHourDetail = useCallback(() => {
    setSelectedHour(null)
    hourTrigger.current?.focus()
  }, [])

  const backToCurrent = useCallback(() => {
    setSelectedHour(null)
    document.getElementById('occupancy-current-heading')?.scrollIntoView?.()
  }, [])

  const filteredName = buildingOptions.find(building => building.id === buildingId)?.name

  const empty = snapshot !== null && snapshot.totals.checkedInPersons === 0 && snapshot.totals.occupiedRooms === 0
  const emptyDay = forecast !== null && forecast.peakExpectedPersons === null && forecast.hours.every(hour => !hour.past)

  return (
    <main id="inhalt" className="admin-statistics-page admin-occupancy-page">
      <h1>Belegung</h1>
      <p className="page-intro">Wie viele Personen sich gerade in den gebuchten Räumen aufhalten.</p>
      {!accessDenied && (
        <form className="statistics-period" onSubmit={event => event.preventDefault()}>
          <Field label="Gebäude">
            {control => (
              <select {...control} value={buildingId} onChange={event => { setSelectedHour(null); setBuildingId(event.target.value) }}>
                <option value="">Alle Gebäude</option>
                {buildingOptions.map(building => <option key={building.id} value={building.id}>{building.name}</option>)}
              </select>
            )}
          </Field>
          {filteredName && <span className="occupancy-filter-note">Gefiltert auf: {filteredName}</span>}
        </form>
      )}
      {loading && <p className="statistics-status" role="status">Belegung wird geladen…</p>}
      {error && <ErrorMessage>{error}</ErrorMessage>}
      {snapshot && (
        <section className="statistics-section" aria-labelledby="occupancy-current-heading">
          <h2 id="occupancy-current-heading">Aktuelle Situation</h2>
          <p className="statistics-period-note">Stand: {formatStamp(snapshot.generatedAt)}</p>
          {stale && (
            <p className="statistics-status occupancy-stale" role="status">
              Die Daten konnten nicht aktualisiert werden und sind möglicherweise veraltet.
            </p>
          )}
          <div className="statistics-cards occupancy-cards">
            <article className="statistics-card" aria-labelledby="occupancy-persons">
              <h3 id="occupancy-persons">Eingecheckte Personen</h3>
              <strong>{snapshot.totals.checkedInPersons}</strong>
              <span>erwartete Teilnehmende eingecheckter Buchungen</span>
            </article>
            <article className="statistics-card" aria-labelledby="occupancy-rooms">
              <h3 id="occupancy-rooms">Belegte Räume</h3>
              <strong>{snapshot.totals.occupiedRooms}</strong>
              <span>Räume mit eingecheckter Buchung</span>
            </article>
          </div>
          {empty && <p className="statistics-status">Derzeit ist kein Raum in Nutzung.</p>}
          {snapshot.buildings.length > 0 && (
            <DataTable
              caption="Belegung je Gebäude"
              columns={[
                {
                  key: 'building',
                  header: 'Gebäude',
                  cell: (building: BuildingOccupancy) =>
                    `${building.buildingName}${building.buildingActive ? '' : ' (inaktiv)'}`,
                },
                { key: 'persons', header: 'Eingecheckte Personen', mono: true, cell: (building: BuildingOccupancy) => building.checkedInPersons },
                { key: 'rooms', header: 'Belegte Räume', mono: true, cell: (building: BuildingOccupancy) => building.occupiedRooms },
              ]}
              rows={snapshot.buildings}
              rowKey={(building) => building.buildingId}
              rowHeader="building"
              emptyText="Keine Gebäude."
            />
          )}
          <h3 id="occupancy-running-heading">Laufende Buchungen</h3>
          {snapshot.runningBookings.length === 0
            ? <p className="statistics-status">Derzeit laufen keine Buchungen.</p>
            : (
              <DataList
                aria-label="Laufende Buchungen"
                items={snapshot.runningBookings}
                itemKey={booking => booking.reservationId}
                renderItem={runningLine}
                emptyText="Derzeit laufen keine Buchungen."
              />
            )}
        </section>
      )}
      {!accessDenied && (
        <section className="statistics-section" aria-labelledby="occupancy-forecast-heading">
          <h2 id="occupancy-forecast-heading">Tagesprognose</h2>
          <p className="statistics-help">
            Geplante Werte aus reservierten und eingecheckten Buchungen: höchste gleichzeitig erwartete Personenzahl je Stunde.
          </p>
          <form className="statistics-period" onSubmit={event => event.preventDefault()}>
            <label>Tag<input aria-label="Tag" type="date" min={todayLocal()} value={day} onChange={event => { setSelectedHour(null); setDay(event.target.value) }} /></label>
          </form>
          {forecastError && <ErrorMessage>{forecastError}</ErrorMessage>}
          {forecast && (
            <>
              {emptyDay && <p className="statistics-status">An diesem Tag sind keine Buchungen vorhanden.</p>}
              <DataTable
                caption="Tagesprognose je Stunde und Gebäude"
                columns={[
                  {
                    key: 'time',
                    header: 'Uhrzeit',
                    mono: true,
                    cell: (hour: HourForecast) =>
                      hour.past
                        ? hour.localLabel
                        : <button type="button" className="occupancy-hour" onClick={event => {
                            hourTrigger.current = event.currentTarget
                            setSelectedHour(hour)
                          }}>{hour.localLabel}</button>,
                  },
                  ...forecast.buildings.map((building, index) => ({
                    key: `building-${building.buildingId}`,
                    header: building.buildingName,
                    mono: true,
                    cell: (hour: HourForecast) => hour.past
                      ? ''
                      : <PlannedValue value={hour.buildings[index].expectedPersons} peak={hour.buildings[index].peak} />,
                  })),
                  {
                    key: 'total',
                    header: 'Gesamt',
                    mono: true,
                    // Vergangene Stunden tragen ihr Wort in der Spalte "Gesamt"; die übrigen Zellen bleiben leer.
                    cell: (hour: HourForecast) => hour.past
                      ? <span className="occupancy-past">vergangen</span>
                      : <PlannedValue value={hour.expectedPersons} peak={hour.peak} />,
                  },
                  { key: 'rooms', header: 'Räume', mono: true, cell: (hour: HourForecast) => hour.past ? '' : hour.bookedRooms },
                ]}
                rows={forecast.hours}
                rowKey={(hour: HourForecast) => hour.start}
                rowHeader="time"
                emptyText="Keine Stunden."
              />
            </>
          )}
          {selectedHour && (
            <HourDetailDialog hour={selectedHour} onClose={closeHourDetail} onBackToCurrent={backToCurrent} />
          )}
        </section>
      )}
    </main>
  )
}

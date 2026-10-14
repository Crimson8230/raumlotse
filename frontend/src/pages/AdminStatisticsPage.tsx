import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { getAdminStatistics } from '../API/adminStatistics'
import { formatApiError } from '../API/client'
import type { AdminStatisticsResponse, RoomStatistics, FeatureStatistics, StatisticsPeriodInput } from '../types/adminStatistics'
import { isValidStatisticsPeriod } from '../types/adminStatistics'
import './AdminStatisticsPage.css'
import { DataTable, ErrorMessage, Field } from '../components/ui'

function formatLocalDate(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

function defaultPeriod(): StatisticsPeriodInput {
  const end = new Date(new Date().getFullYear(), new Date().getMonth(), 1)
  const start = new Date(end.getFullYear(), end.getMonth() - 12, 1)
  return { from: formatLocalDate(start), to: formatLocalDate(end) }
}

function formatHours(seconds: number): string {
  return `${(seconds / 3600).toLocaleString('de-DE', { maximumFractionDigits: 2 })} h`
}

function formatNumber(value: number): string {
  return value.toLocaleString('de-DE', { maximumFractionDigits: 2, minimumFractionDigits: Number.isInteger(value) ? 0 : 1 })
}

function sortBy<T>(values: T[], key: (value: T) => string | number, descending: boolean): T[] {
  return [...values].sort((a, b) => {
    const left = key(a)
    const right = key(b)
    const result = typeof left === 'number' && typeof right === 'number'
      ? left - right
      : String(left).localeCompare(String(right), 'de')
    return descending ? -result : result
  })
}

export default function AdminStatisticsPage() {
  const [draft, setDraft] = useState<StatisticsPeriodInput>(defaultPeriod)
  const [period, setPeriod] = useState<StatisticsPeriodInput>(defaultPeriod)
  const [data, setData] = useState<AdminStatisticsResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [roomDescending, setRoomDescending] = useState(true)
  const [featureDescending, setFeatureDescending] = useState(true)

  useEffect(() => {
    let active = true
    void getAdminStatistics(period).then((loaded) => {
      if (active) setData(loaded)
    }).catch((reason: unknown) => {
      if (active) setError(formatApiError(reason))
    }).finally(() => {
      if (active) setLoading(false)
    })
    return () => { active = false }
  }, [period])

  function applyPeriod(event: FormEvent) {
    event.preventDefault()
    if (!isValidStatisticsPeriod(draft)) {
      setError('Bitte ein gültiges Start- und Enddatum auswählen.')
      return
    }
    setData(null)
    setLoading(true)
    setError(null)
    setPeriod(draft)
  }

  const sortedRooms = data ? sortBy(data.rooms, room => room.utilizationPercent, roomDescending) : []
  const sortedFeatures = data ? sortBy(data.features, feature => feature.bookingCount, featureDescending) : []
  const noReservations = data?.summary.totalReservations === 0

  return (
    <main id="inhalt" className="admin-statistics-page">
      <h1>Admin-Statistiken</h1>
      <p className="page-intro">Raumnutzung und Buchungen für den ausgewählten Zeitraum.</p>
      <form className="statistics-period" onSubmit={applyPeriod}>
        <Field label="Von">
          {control => <input {...control} type="date" value={draft.from} onChange={event => setDraft({ ...draft, from: event.target.value })} />}
        </Field>
        <Field label="Bis">
          {control => <input {...control} type="date" value={draft.to} onChange={event => setDraft({ ...draft, to: event.target.value })} />}
        </Field>
        <button type="submit">Zeitraum anwenden</button>
      </form>
      {loading && <p className="statistics-status" role="status">Statistiken werden geladen…</p>}
      {error && <ErrorMessage>{error}</ErrorMessage>}
      {!loading && data && (
        <>
          <p className="statistics-period-note">Zeitraum: {data.period.from} bis {data.period.to} · Zeitzone: {data.period.timezone}</p>
          {noReservations && <p className="statistics-status" role="status">Für diesen Zeitraum liegen keine Buchungen vor.</p>}
          <section className="statistics-cards" aria-label="Zusammenfassung">
            <article className="statistics-card"><h2>Durchschnitt Personen</h2><strong>{data.summary.averageExpectedAttendees === null ? '—' : formatNumber(data.summary.averageExpectedAttendees)}</strong><span>{data.summary.attendeeBookingCount} gültige Buchungen</span></article>
            <article className="statistics-card"><h2>Stornierungen</h2><strong>{data.summary.cancelledReservationCount}</strong><span>{data.summary.cancellationRatePercent === null ? '—' : `${formatNumber(data.summary.cancellationRatePercent)} %`} von {data.summary.totalReservations} Buchungen</span></article>
            <article className="statistics-card"><h2>Datengrundlage</h2><strong>{data.summary.validReservationCount}</strong><span>gültige Buchungen · {data.summary.missingAttendeeCount} ohne Personenangabe</span></article>
          </section>
          <section className="statistics-section"><div className="statistics-section-heading"><h2>Raumauslastung</h2><button type="button" onClick={() => setRoomDescending(value => !value)}>Nach Auslastung sortieren {roomDescending ? '↓' : '↑'}</button></div><p className="statistics-help">Auslastung = gebuchte Raumzeit geteilt durch die gesamte Kalenderzeit des Zeitraums.</p>
            <DataTable
              caption="Raumauslastung je Raum"
              columns={[
                { key: 'room', header: 'Raum', cell: (room: RoomStatistics) => <span className="room-name">{room.roomName}</span> },
                { key: 'status', header: 'Status', cell: (room: RoomStatistics) => room.roomStatus },
                { key: 'bookings', header: 'Buchungen', mono: true, cell: (room: RoomStatistics) => room.bookingCount },
                { key: 'time', header: 'Gebuchte Zeit', mono: true, cell: (room: RoomStatistics) => formatHours(room.bookedSeconds) },
                { key: 'utilization', header: 'Auslastung', mono: true, cell: (room: RoomStatistics) => `${formatNumber(room.utilizationPercent)} %` },
              ]}
              rows={sortedRooms}
              rowKey={(room: RoomStatistics) => room.roomId}
              rowHeader="room"
              emptyText="Keine Räume im ausgewählten Zeitraum."
            />
          </section>
          <section className="statistics-section"><div className="statistics-section-heading"><h2>Raumfeatures</h2><button type="button" onClick={() => setFeatureDescending(value => !value)}>Nach Nutzung sortieren {featureDescending ? '↓' : '↑'}</button></div><p className="statistics-help">Ein Raum mit mehreren Features zählt je Feature, aber nicht mehrfach in den Gesamtzahlen.</p>
            <DataTable
              caption="Nutzung der Raumfeatures"
              columns={[
                { key: 'feature', header: 'Feature', cell: (feature: FeatureStatistics) => feature.featureName },
                { key: 'status', header: 'Status', cell: (feature: FeatureStatistics) => feature.featureStatus },
                { key: 'bookings', header: 'Buchungen', mono: true, cell: (feature: FeatureStatistics) => feature.bookingCount },
                { key: 'time', header: 'Gebuchte Zeit', mono: true, cell: (feature: FeatureStatistics) => formatHours(feature.bookedSeconds) },
              ]}
              rows={sortedFeatures}
              rowKey={(feature: FeatureStatistics) => feature.featureId}
              rowHeader="feature"
              emptyText="Keine Raumfeatures im ausgewählten Zeitraum."
            />
          </section>
        </>
      )}
    </main>
  )
}

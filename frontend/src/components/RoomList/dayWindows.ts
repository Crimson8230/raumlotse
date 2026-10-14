import { listRoomReservations } from '../../API/reservations'
import type { TimeWindow } from '../ui'
import type { Reservation } from '../../types/reservation'

/** Anzeigebereich der Streifen in der Raumliste (lokale Zeit). Kein Fachwert: es gibt keine Öffnungszeiten. */
export const STRIP_DAY_START = 7
export const STRIP_DAY_END = 20
/** Höchstzahl gleichzeitiger Belegungsabfragen der Raumliste (FR-020). */
export const MAX_CONCURRENT_QUERIES = 4
/** Nach dieser Zeit gelten geladene Zeitfenster als veraltet. */
export const REFRESH_MS = 5 * 60 * 1000

export function dayRange(now: Date): { start: Date; end: Date } {
  const start = new Date(now)
  start.setHours(STRIP_DAY_START, 0, 0, 0)
  const end = new Date(now)
  end.setHours(STRIP_DAY_END, 0, 0, 0)
  return { start, end }
}

/** Storniert und verfallen zählt nicht; abgeschlossen war der Raum belegt; eigene Buchungen sind "meine". */
export function toStripWindows(reservations: Reservation[]): TimeWindow[] {
  return reservations
    .filter((r) => r.status !== 'CANCELLED' && r.status !== 'EXPIRED')
    .map((r) => ({ start: r.startTime, end: r.endTime, state: r.ownedByMe ? 'meine' : 'belegt' }))
}

interface CacheEntry {
  loadedAt: number
  windows: TimeWindow[]
}

const cache = new Map<string, CacheEntry>()
const inFlight = new Map<string, Promise<TimeWindow[]>>()
const waiting: Array<() => void> = []
let running = 0

export function resetDayWindowsCache(): void {
  cache.clear()
  inFlight.clear()
  waiting.length = 0
  running = 0
}

/** Letzter bekannter Stand (auch veraltet), damit eine Zeile beim Aktualisieren ihre Fenster behält. */
export function cachedDayWindows(roomId: string, now: Date): TimeWindow[] | undefined {
  return cache.get(key(roomId, now))?.windows
}

function key(roomId: string, now: Date): string {
  return `${roomId}|${dayRange(now).start.toISOString()}`
}

function acquire(): Promise<void> {
  if (running < MAX_CONCURRENT_QUERIES) {
    running += 1
    return Promise.resolve()
  }
  return new Promise((resolve) => waiting.push(resolve))
}

function release(): void {
  const next = waiting.shift()
  if (next) next()
  else running -= 1
}

/**
 * Zeitfenster eines Raums für den Streifen des Tages. Pro Raum und Tag wird die vorhandene Belegungsabfrage
 * genutzt (kein Sammelendpunkt, Feature 019); gleichzeitig laufen höchstens {@link MAX_CONCURRENT_QUERIES}.
 */
export function loadDayWindows(roomId: string, now: Date): Promise<TimeWindow[]> {
  const k = key(roomId, now)
  const hit = cache.get(k)
  if (hit && Date.now() - hit.loadedAt < REFRESH_MS) return Promise.resolve(hit.windows)
  const pending = inFlight.get(k)
  if (pending) return pending

  const { start, end } = dayRange(now)
  const promise = (async () => {
    await acquire()
    try {
      const reservations = await listRoomReservations(roomId, start.toISOString(), end.toISOString())
      const windows = toStripWindows(reservations)
      cache.set(k, { loadedAt: Date.now(), windows })
      return windows
    } finally {
      release()
    }
  })().finally(() => inFlight.delete(k))
  inFlight.set(k, promise)
  return promise
}

import { useEffect, useRef, useState } from 'react'
import type { TimeWindow } from '../ui'
import { cachedDayWindows, loadDayWindows } from './dayWindows'

export type StripLoadState = 'loading' | 'ready' | 'failed'

export interface RoomDayWindows {
  state: StripLoadState
  windows: TimeWindow[]
}

/** Ohne IntersectionObserver (z. B. Testumgebung) werden nur die ersten Zeilen geladen (FR-020). */
export const INITIAL_ROWS_WITHOUT_OBSERVER = 20

/** Wahr, sobald das Element einmal sichtbar war; bleibt danach wahr, damit die Zeile nicht erneut lädt. */
export function useBecameVisible(index: number): [React.RefObject<HTMLLIElement | null>, boolean] {
  const ref = useRef<HTMLLIElement | null>(null)
  const supported = typeof IntersectionObserver !== 'undefined'
  const [seen, setSeen] = useState(false)

  useEffect(() => {
    const element = ref.current
    if (!supported || !element || seen) return
    const observer = new IntersectionObserver((entries) => {
      if (entries.some((entry) => entry.isIntersecting)) {
        setSeen(true)
        observer.disconnect()
      }
    })
    observer.observe(element)
    return () => observer.disconnect()
  }, [supported, seen])

  return [ref, supported ? seen : index < INITIAL_ROWS_WITHOUT_OBSERVER]
}

/**
 * Zeitfenster eines Raums für den Streifen. Lädt erst, wenn `enabled`; `now` kommt von der Seite und tickt
 * minütlich, dabei werden veraltete Daten (älter als 5 Minuten) neu abgefragt.
 */
export function useRoomDayWindows(roomId: string, now: Date, enabled: boolean): RoomDayWindows {
  const minute = Math.floor(now.getTime() / 60000)
  const [result, setResult] = useState<RoomDayWindows>(() => {
    const cached = cachedDayWindows(roomId, now)
    return cached ? { state: 'ready', windows: cached } : { state: 'loading', windows: [] }
  })

  useEffect(() => {
    if (!enabled) return
    let ignore = false
    loadDayWindows(roomId, new Date(minute * 60000)).then(
      (windows) => {
        if (!ignore) setResult({ state: 'ready', windows })
      },
      () => {
        if (!ignore) setResult((previous) => ({ state: 'failed', windows: previous.windows }))
      },
    )
    return () => {
      ignore = true
    }
  }, [roomId, enabled, minute])

  return result
}

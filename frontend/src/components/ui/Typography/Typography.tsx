import type { ReactNode } from 'react'

/** Raumname in Serif; nur eine Klasse, der Text bleibt unverändert (auch für Screenreader). */
export function RoomName({ children }: { children: ReactNode }) {
  return <span className="room-name">{children}</span>
}

/** Uhrzeit, Zeitraum oder Zahl in Monospace. */
export function TimeText({ children }: { children: ReactNode }) {
  return <span className="mono">{children}</span>
}

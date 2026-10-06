import type { ReservationStatus } from '../types/reservation'

/** Anzeigetexte für Statuswerte der API (Gebäude, Stockwerke, Räume, Ausstattungstypen). */
export function entityStatusLabel(status: string): string {
  return status === 'ACTIVE' ? 'Aktiv' : 'Deaktiviert'
}

export const reservationStatusLabels: Record<ReservationStatus, string> = {
  RESERVED: 'Reserviert',
  ACTIVE: 'Aktiv',
  COMPLETED: 'Abgeschlossen',
  EXPIRED: 'Abgelaufen',
  CANCELLED: 'Storniert',
}

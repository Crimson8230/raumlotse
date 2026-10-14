/** Zuordnung technischer Feldnamen aus Validierungsfehlern zu Beschriftungen (Feldnamen der Backend-Requests). */
export const defaultFieldLabels: Record<string, string> = {
  startTime: 'Beginn',
  endTime: 'Ende',
  roomId: 'Raum',
  floorId: 'Stockwerk',
  buildingId: 'Haus',
  name: 'Name',
  code: 'Kürzel',
  email: 'E-Mail',
  password: 'Passwort',
  expectedAttendees: 'Erwartete Teilnehmende',
  maxCapacity: 'Höchstkapazität',
  minPersons: 'Mindestpersonenzahl',
  maxPersons: 'Höchstpersonenzahl',
  reservedFor: 'Reserviert für',
  note: 'Notiz',
  seatingArrangementId: 'Sitzordnung',
  equipmentTypeId: 'Ausstattung',
  groundFloor: 'Erdgeschoss',
  hasElevator: 'Aufzug',
  state: 'Status',
  additionalEquipmentTypeIds: 'Zusätzliche Ausstattung',
  seatingArrangements: 'Sitzordnung',
  barrierFreeReachable: 'Barrierefrei erreichbar',
  earlyCheckInMinutes: 'Frühester Check-in (Minuten)',
  gracePeriodMinutes: 'Karenzzeit (Minuten)',
  roles: 'Rollen',
}

function humanize(name: string): string {
  const words = name
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .replace(/[_-]+/g, ' ')
    .trim()
    .toLowerCase()
  return words.charAt(0).toUpperCase() + words.slice(1)
}

/** Beschriftung zu einem Feldnamen; Seiten ergänzen oder überschreiben über `overrides`. */
export function fieldLabel(name: string, overrides?: Record<string, string>): string {
  return overrides?.[name] ?? defaultFieldLabels[name] ?? humanize(name)
}

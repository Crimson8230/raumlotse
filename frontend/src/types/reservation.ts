export type ReservationStatus = 'RESERVED' | 'ACTIVE' | 'COMPLETED' | 'EXPIRED' | 'CANCELLED'

export interface SeatingArrangementSummary {
  id: string
  name: string
  maxCapacity: number
}

export interface EquipmentTypeSummary {
  id: string
  name: string
  status: 'ACTIVE' | 'DEACTIVATED'
  code?: string
}

export interface Reservation {
  id: string
  roomId: string
  roomName: string
  startTime: string
  endTime: string
  status: ReservationStatus
  /**
   * Entries of other users' reservations in a shared room schedule are redacted (feature 013, FR-023): only the time
   * window and status are present, personal details are null/empty and `ownedByMe` is false.
   */
  seatingArrangement: SeatingArrangementSummary | null
  expectedAttendees: number | null
  additionalEquipment: EquipmentTypeSummary[]
  note?: string | null
  createdBy: string | null
  reservedFor: string | null
  createdAt: string | null
  ownedByMe: boolean
}

export interface ReservationCreatePayload {
  startTime: string
  endTime: string
  seatingArrangementId: string
  expectedAttendees: number
  reservedFor: string
  emailNotification: boolean
  additionalEquipmentTypeIds?: string[]
  note?: string
  createdBy?: string
}

export interface ReservationUpdatePayload {
  expectedAttendees?: number
  note?: string
  reservedFor?: string
}

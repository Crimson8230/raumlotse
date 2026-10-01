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
  seatingArrangement: SeatingArrangementSummary
  expectedAttendees: number
  additionalEquipment: EquipmentTypeSummary[]
  note?: string | null
  createdBy: string
  reservedFor: string
  createdAt: string
}

export interface ReservationCreatePayload {
  startTime: string
  endTime: string
  seatingArrangementId: string
  expectedAttendees: number
  reservedFor: string
  additionalEquipmentTypeIds?: string[]
  note?: string
  createdBy?: string
}

export interface ReservationUpdatePayload {
  expectedAttendees?: number
  note?: string
  reservedFor?: string
}

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
  createdAt: string
}

export interface ReservationCreatePayload {
  startTime: string
  endTime: string
  seatingArrangementId: string
  expectedAttendees: number
  additionalEquipmentTypeIds?: string[]
  note?: string
  createdBy: string
}

export interface ReservationUpdatePayload {
  expectedAttendees?: number
  note?: string
}

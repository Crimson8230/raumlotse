export type EntityStatus = 'ACTIVE' | 'DEACTIVATED'

export interface Building {
  id: string
  name: string
  status: EntityStatus
}

export interface Floor {
  id: string
  buildingId: string
  name: string
  status: EntityStatus
}

export interface EquipmentType {
  id: string
  name: string
  status: EntityStatus
}

export interface SeatingArrangement {
  id?: string
  name: string
  maxCapacity: number
}

export interface RoomSummary {
  id: string
  name: string
  building: Building
  floor: Floor
  status: EntityStatus
}

export interface Room extends RoomSummary {
  version: number
  seatingArrangements: SeatingArrangement[]
  equipmentTypeIds: string[]
}

export interface RoomCreateRequest {
  name: string
  floorId: string
  seatingArrangements: SeatingArrangement[]
  equipmentTypeIds?: string[]
}

export interface RoomUpdateRequest extends RoomCreateRequest {
  version: number
}

export interface ProblemFieldError {
  field: string
  message: string
}

export interface Problem {
  title: string
  status: number
  detail: string
  errors?: ProblemFieldError[]
}

export type StatusFilter = 'active' | 'deactivated' | 'all'

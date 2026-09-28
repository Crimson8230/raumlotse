export type EntityStatus = 'ACTIVE' | 'DEACTIVATED'

export interface Building {
  id: string
  name: string
  status: EntityStatus
  hasElevator: boolean
}

export interface Floor {
  id: string
  buildingId: string
  name: string
  status: EntityStatus
  /** Step-free entrance level (feature 008). */
  groundFloor: boolean
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
  /** Administrator exclusion (feature 008). */
  notBarrierFree: boolean
  /** Derived by the backend: (ground floor OR elevator) AND NOT notBarrierFree. */
  barrierFreeReachable: boolean
}

export interface RoomCreateRequest {
  name: string
  floorId: string
  seatingArrangements: SeatingArrangement[]
  equipmentTypeIds?: string[]
  /** Omitted on update keeps the current value. */
  notBarrierFree?: boolean
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
  code?: string
  retryAfterSeconds?: number
}

export type StatusFilter = 'active' | 'deactivated' | 'all'

/**
 * Room search filters as edited in the search panel and mirrored in the `/rooms` URL (feature 008).
 * Numbers stay strings so invalid input can be shown and validated instead of being coerced away.
 */
export interface RoomSearchFormState {
  minPersons: string
  maxPersons: string
  buildingId: string
  seatingArrangement: string
  equipmentTypeIds: string[]
  barrierFree: boolean
  date: string
  startTime: string
  endTime: string
}

export const emptySearchFormState: RoomSearchFormState = {
  minPersons: '',
  maxPersons: '',
  buildingId: '',
  seatingArrangement: '',
  equipmentTypeIds: [],
  barrierFree: false,
  date: '',
  startTime: '',
  endTime: '',
}

export type CheckInStatus = 'CHECKED_IN' | 'NOT_CHECKED_IN'

export interface BookingEntry {
  reservationId: string
  roomId: string
  roomName: string
  buildingId: string
  buildingName: string
  floorId: string
  floorName: string
  startTime: string
  endTime: string
  expectedAttendees: number
  checkInStatus: CheckInStatus
  checkedInAt: string | null
}

export interface BuildingOccupancy {
  buildingId: string
  buildingName: string
  buildingActive: boolean
  checkedInPersons: number
  occupiedRooms: number
}

export interface OccupancySnapshot {
  generatedAt: string
  buildingId: string | null
  totals: { checkedInPersons: number; occupiedRooms: number }
  buildings: BuildingOccupancy[]
  runningBookings: BookingEntry[]
}

export interface ForecastBuilding {
  buildingId: string
  buildingName: string
  buildingActive: boolean
  peakExpectedPersons: number | null
}

export interface HourBuildingValue {
  buildingId: string
  expectedPersons: number | null
  bookedRooms: number | null
  peak: boolean
}

export interface FloorGroup {
  buildingId: string
  buildingName: string
  floorId: string
  floorName: string
  expectedPersons: number
  bookings: BookingEntry[]
}

export interface HourForecast {
  start: string
  end: string
  localLabel: string
  past: boolean
  expectedPersons: number | null
  bookedRooms: number | null
  peak: boolean
  buildings: HourBuildingValue[]
  floors: FloorGroup[]
}

export interface OccupancyForecast {
  date: string
  zone: string
  generatedAt: string
  buildingId: string | null
  peakExpectedPersons: number | null
  buildings: ForecastBuilding[]
  hours: HourForecast[]
}

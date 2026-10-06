export interface AdminStatisticsPeriod {
  from: string
  to: string
  timezone: string
}

export interface SummaryStatistics {
  totalReservations: number
  validReservationCount: number
  cancelledReservationCount: number
  cancellationRatePercent: number | null
  attendeeSum: number
  attendeeBookingCount: number
  averageExpectedAttendees: number | null
  missingAttendeeCount: number
}

export interface RoomStatistics {
  roomId: string
  roomName: string
  roomStatus: 'ACTIVE' | 'DEACTIVATED'
  bookingCount: number
  bookedSeconds: number
  utilizationPercent: number
}

export interface FeatureStatistics {
  featureId: string
  featureName: string
  featureStatus: 'ACTIVE' | 'DEACTIVATED'
  bookingCount: number
  bookedSeconds: number
}

export interface AdminStatisticsResponse {
  period: AdminStatisticsPeriod
  summary: SummaryStatistics
  rooms: RoomStatistics[]
  features: FeatureStatistics[]
}

export interface StatisticsPeriodInput {
  from: string
  to: string
}

export function isValidStatisticsPeriod(period: StatisticsPeriodInput): boolean {
  return Boolean(period.from && period.to && period.from < period.to)
}

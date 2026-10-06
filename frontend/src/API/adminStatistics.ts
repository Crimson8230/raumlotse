import { apiRequest } from './client'
import type { AdminStatisticsResponse, StatisticsPeriodInput } from '../types/adminStatistics'

export function getAdminStatistics(period: StatisticsPeriodInput): Promise<AdminStatisticsResponse> {
  const query = new URLSearchParams({ from: period.from, to: period.to })
  return apiRequest<AdminStatisticsResponse>(`/api/admin/statistics?${query.toString()}`)
}

import { describe, expect, it } from 'vitest'
import { formatDate, formatDateTime, formatDateTimeRange, formatTime } from './date'

describe('date utils', () => {
  it('formats a date as DD.MM.YYYY', () => {
    const d = new Date(2026, 8, 19, 14, 30) // 19 Sep 2026 14:30
    expect(formatDate(d)).toBe('19.09.2026')
  })

  it('formats a time as HH:mm with 24-hour padding', () => {
    const d = new Date(2026, 8, 19, 9, 5) // 09:05
    expect(formatTime(d)).toBe('09:05')
  })

  it('formats datetime as DD.MM.YYYY, HH:mm', () => {
    const d = new Date(2026, 8, 19, 10, 15)
    expect(formatDateTime(d.toISOString())).toBe('19.09.2026, 10:15')
  })

  it('handles invalid datetime gracefully', () => {
    expect(formatDateTime('invalid-date')).toBe('invalid-date')
  })

  it('formats same-day time range concisely without repeating date', () => {
    const start = new Date(2026, 9, 1, 9, 0) // 01 Oct 2026 09:00
    const end = new Date(2026, 9, 1, 11, 30) // 01 Oct 2026 11:30
    expect(formatDateTimeRange(start.toISOString(), end.toISOString())).toBe(
      '01.10.2026, 09:00 – 11:30',
    )
  })

  it('formats multi-day time range with both dates', () => {
    const start = new Date(2026, 9, 1, 9, 0) // 01 Oct 2026 09:00
    const end = new Date(2026, 9, 2, 17, 0) // 02 Oct 2026 17:00
    expect(formatDateTimeRange(start.toISOString(), end.toISOString())).toBe(
      '01.10.2026, 09:00 – 02.10.2026, 17:00',
    )
  })

  it('handles invalid time range gracefully', () => {
    expect(formatDateTimeRange('bad-start', 'bad-end')).toBe('bad-start – bad-end')
  })
})

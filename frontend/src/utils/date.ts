function padZero(num: number): string {
  return String(num).padStart(2, '0')
}

export function formatDate(d: Date): string {
  const day = padZero(d.getDate())
  const month = padZero(d.getMonth() + 1)
  const year = d.getFullYear()
  return `${day}.${month}.${year}`
}

export function formatTime(d: Date): string {
  const hours = padZero(d.getHours())
  const minutes = padZero(d.getMinutes())
  return `${hours}:${minutes}`
}

/** ISO instant → `YYYY-MM-DDTHH:mm` in local time, the value format of `<input type="datetime-local">`. */
export function toDateTimeLocalValue(iso: string): string {
  const d = new Date(iso)
  if (isNaN(d.getTime())) return ''
  return `${d.getFullYear()}-${padZero(d.getMonth() + 1)}-${padZero(d.getDate())}T${formatTime(d)}`
}

export function formatDateTime(isoString: string): string {
  const d = new Date(isoString)
  if (isNaN(d.getTime())) return isoString
  return `${formatDate(d)}, ${formatTime(d)}`
}

export function formatDateTimeRange(startIso: string, endIso: string): string {
  const startDate = new Date(startIso)
  const endDate = new Date(endIso)
  if (isNaN(startDate.getTime()) || isNaN(endDate.getTime())) {
    return `${startIso} – ${endIso}`
  }

  const isSameDay =
    startDate.getFullYear() === endDate.getFullYear() &&
    startDate.getMonth() === endDate.getMonth() &&
    startDate.getDate() === endDate.getDate()

  if (isSameDay) {
    return `${formatDate(startDate)}, ${formatTime(startDate)} – ${formatTime(endDate)}`
  }

  return `${formatDate(startDate)}, ${formatTime(startDate)} – ${formatDate(endDate)}, ${formatTime(endDate)}`
}

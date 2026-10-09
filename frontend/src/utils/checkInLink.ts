export type CheckInLinkMethod = 'qr' | 'nfc'

/**
 * The room's on-site check-in link (feature 014). The QR code encodes the `qr` variant; the `nfc` variant is the same
 * link for writing onto an NFC tag. Built from the current origin so it works in every environment.
 */
export function checkInLink(roomId: string, method: CheckInLinkMethod): string {
  return `${window.location.origin}/rooms/${encodeURIComponent(roomId)}/check-in?method=${method}`
}

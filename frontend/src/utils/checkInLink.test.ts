import { describe, expect, it } from 'vitest'
import { checkInLink } from './checkInLink'

describe('checkInLink', () => {
  it('builds the QR check-in link from the current origin', () => {
    expect(checkInLink('r1', 'qr')).toBe(`${window.location.origin}/rooms/r1/check-in?method=qr`)
  })

  it('builds the NFC variant of the same link', () => {
    expect(checkInLink('r1', 'nfc')).toBe(`${window.location.origin}/rooms/r1/check-in?method=nfc`)
  })

  it('encodes the room id', () => {
    expect(checkInLink('a/b', 'qr')).toBe(`${window.location.origin}/rooms/a%2Fb/check-in?method=qr`)
  })
})

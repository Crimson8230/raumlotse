import { describe, expect, it } from 'vitest'
import { safeReturnPath } from './returnPath'

describe('safeReturnPath', () => {
  it('keeps same-app paths including the query string', () => {
    expect(safeReturnPath('/rooms/r1/check-in?method=nfc')).toBe('/rooms/r1/check-in?method=nfc')
  })

  it.each([undefined, null, 42, '', 'rooms/r1', '//evil.example', 'https://evil.example', '/\\evil.example'])(
    'falls back to the start page for %s', (from) => {
      expect(safeReturnPath(from)).toBe('/')
    })
})

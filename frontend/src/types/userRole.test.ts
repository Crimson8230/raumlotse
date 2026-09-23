import { describe, expect, it } from 'vitest'
import { roleOptions, isValidRoleSelection } from './userRole'
describe('role selection', () => {
  it('accepts all 31 unique nonempty sets and rejects malformed values', () => {
    for (let mask = 1; mask < 32; mask++) {
      expect(isValidRoleSelection(roleOptions.filter((_, i) => mask & (1 << i)).map(r => r.code))).toBe(true)
    }
    for (const invalid of [[], null, undefined, ['UNKNOWN'], ['VIEWER', 'VIEWER'], [null], 'ADMIN']) {
      expect(isValidRoleSelection(invalid)).toBe(false)
    }
  })
})


import { describe, expect, it } from 'vitest'
import type { RoomDeviceCommandRequest, RoomDeviceKind } from './roomDevice'

describe('room device contract types', () => {
  it('supports only documented capabilities and boolean commands at compile time', () => {
    const kind: RoomDeviceKind = 'PROJECTOR'
    const command: RoomDeviceCommandRequest = { state: true }
    expect([kind, command.state]).toEqual(['PROJECTOR', true])
  })
})

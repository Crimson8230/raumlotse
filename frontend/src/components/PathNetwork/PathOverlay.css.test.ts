/// <reference types="node" />
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

const css = readFileSync(join(dirname(fileURLToPath(import.meta.url)), 'PathOverlay.css'), 'utf8')

describe('path overlay stylesheet', () => {
  it('uses colour tokens only', () => {
    expect(css).not.toMatch(/#[0-9a-fA-F]{3,8}\b|\brgba?\(|\bhsla?\(/)
  })

  it('hides the editing tools on narrow screens but keeps the overlay itself viewable', () => {
    const media = /@media \(max-width: 799px\) \{([\s\S]*?)\n\}/.exec(css)
    expect(media).not.toBeNull()
    expect(media![1]).toContain('.path-toolbar')
    expect(media![1]).not.toContain('.path-overlay')
  })

  it('lets only waypoints and segment hit areas receive pointer events', () => {
    expect(/\.path-overlay\s*\{[^}]*pointer-events:\s*none/.test(css)).toBe(true)
    expect(/\.path-waypoint\s*\{[^}]*pointer-events:\s*auto/.test(css)).toBe(true)
  })
})

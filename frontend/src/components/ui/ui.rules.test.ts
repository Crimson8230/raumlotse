/// <reference types="node" />
import { readdirSync, readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

const here = dirname(fileURLToPath(import.meta.url))
const tokensCss = readFileSync(join(here, '../../index.css'), 'utf8')

function collectCss(dir: string): Record<string, string> {
  const found: Record<string, string> = {}
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const path = join(dir, entry.name)
    if (entry.isDirectory()) Object.assign(found, collectCss(path))
    else if (entry.name.endsWith('.css')) found[path] = readFileSync(path, 'utf8')
  }
  return found
}

const blockCss = collectCss(here)

function token(name: string): string {
  const match = new RegExp(`--${name}:\\s*(#[0-9a-fA-F]{6})\\s*;`).exec(tokensCss)
  if (!match) throw new Error(`Token --${name} not found in index.css`)
  return match[1]
}

function luminance(hex: string): number {
  const channels = [1, 3, 5].map((i) => {
    const c = parseInt(hex.slice(i, i + 2), 16) / 255
    return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4)
  })
  return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2]
}

function contrast(foreground: string, background: string): number {
  const [a, b] = [luminance(foreground), luminance(background)].sort((x, y) => y - x)
  return (a + 0.05) / (b + 0.05)
}

function ruleBody(css: string, selector: string): string {
  const escaped = selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const match = new RegExp(`${escaped}\\s*\\{([^}]*)\\}`).exec(css)
  if (!match) throw new Error(`Rule ${selector} not found`)
  return match[1]
}

const allBlockCss = Object.values(blockCss).join('\n')

describe('block stylesheets (FR-011, FR-012, FR-013)', () => {
  it('finds the block stylesheets', () => {
    expect(Object.keys(blockCss).length).toBeGreaterThan(0)
  })

  it('contains no fixed color values', () => {
    const literal = /#[0-9a-fA-F]{3,8}\b|\brgba?\(|\bhsla?\(/
    for (const [file, css] of Object.entries(blockCss)) {
      expect(css, file).not.toMatch(literal)
    }
  })

  it.each([
    ['color-text', 'color-background'],
    ['color-text', 'color-surface'],
    ['color-muted', 'color-background'],
    ['color-muted', 'color-surface'],
    ['color-background', 'color-text'],
    ['color-primary', 'color-background'],
    ['color-primary', 'color-surface'],
    ['color-error', 'color-background'],
    ['color-error', 'color-surface'],
  ])('text pair %s on %s reaches 7:1', (foreground, background) => {
    expect(contrast(token(foreground), token(background))).toBeGreaterThanOrEqual(7)
  })

  it('gives interactive targets at least 44 px', () => {
    const body = ruleBody(allBlockCss, '.ui-target')
    expect(body).toMatch(/min-height:\s*44px/)
    expect(body).toMatch(/min-width:\s*44px/)
  })

  it('keeps the skip link available to assistive technology while hidden', () => {
    const body = ruleBody(allBlockCss, '.ui-skip-link')
    expect(body).not.toMatch(/display:\s*none|visibility:\s*hidden/)
  })

  it('uses the serif face for room names and the mono face for times and numbers', () => {
    expect(ruleBody(allBlockCss, '.ui-strip__name')).toMatch(/var\(--font-serif\)/)
    expect(ruleBody(allBlockCss, '.ui-wayfinding__target')).toMatch(/var\(--font-serif\)/)
    expect(ruleBody(allBlockCss, '.ui-strip__time')).toMatch(/var\(--font-mono\)/)
    expect(ruleBody(allBlockCss, '.ui-table__cell--mono')).toMatch(/var\(--font-mono\)/)
    expect(ruleBody(allBlockCss, '.ui-wayfinding__distance')).toMatch(/var\(--font-mono\)/)
  })

  it('does not force fixed widths or no-wrap text that would break phone width or 200 % zoom', () => {
    expect(allBlockCss).not.toMatch(/white-space:\s*nowrap/)
    expect(allBlockCss).not.toMatch(/(^|[;{\s])width:\s*(?!1px)\d+px/)
  })
})

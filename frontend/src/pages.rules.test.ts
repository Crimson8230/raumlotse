/// <reference types="node" />
import { readdirSync, readFileSync } from 'node:fs'
import { dirname, join, relative, sep } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

// Source-level rules of feature 019 (contracts/ui-pages.md): they keep new pages from falling back.
const here = dirname(fileURLToPath(import.meta.url))

function collect(dir: string): string[] {
  return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const path = join(dir, entry.name)
    if (entry.isDirectory()) return collect(path)
    return entry.name.endsWith('.tsx') && !entry.name.includes('.test.') ? [path] : []
  })
}

function withoutComments(source: string): string {
  return source.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(^|[^:])\/\/.*$/gm, '$1')
}

const sources = collect(here)
  .filter((path) => !relative(here, path).split(sep).includes('ui'))
  .map((path) => ({ file: relative(here, path), text: withoutComments(readFileSync(path, 'utf8')) }))

function offenders(pattern: RegExp, allowed: (line: string) => boolean = () => false): string[] {
  return sources.flatMap(({ file, text }) =>
    text
      .split('\n')
      .map((line, index) => ({ line, number: index + 1 }))
      .filter(({ line }) => pattern.test(line) && !allowed(line))
      .map(({ number }) => `${file}:${number}`),
  )
}

describe('page rules (feature 019)', () => {
  it('(a) every <main> is the skip-link target id="inhalt"', () => {
    // A <main> may span several lines; look at the opening tag up to its closing ">".
    const bad = sources.flatMap(({ file, text }) =>
      [...text.matchAll(/<main\b[^>]*>/g)].filter((m) => !/id="inhalt"/.test(m[0])).map(() => file),
    )
    expect(bad).toEqual([])
  })

  it('(b) no raw <table> outside components/ui', () => {
    expect(offenders(/<table\b/)).toEqual([])
  })

  it('(c) no feedback-error paragraphs', () => {
    expect(offenders(/feedback-error/)).toEqual([])
  })

  it('(d) submit buttons are primary, except the search and filter forms', () => {
    const allowedSecondary = ['Suchen', 'Zeitraum anwenden']
    const bad = sources.flatMap(({ file, text }) =>
      [...text.matchAll(/<button\b[^>]*type="submit"[^>]*>([^<]*)</g)]
        .filter((m) => !/btn-primary/.test(m[0]) && !allowedSecondary.includes(m[1].trim()))
        .map((m) => `${file}: ${m[1].trim()}`),
    )
    expect(bad).toEqual([])
  })

  it('(e) a form has at most one primary action', () => {
    const bad = sources.flatMap(({ file, text }) =>
      [...text.matchAll(/<form\b[\s\S]*?<\/form>/g)]
        .filter((m) => (m[0].match(/btn-primary/g) ?? []).length > 1)
        .map(() => file),
    )
    expect(bad).toEqual([])
  })

  it('(f) the room row and navigation styles use tokens only', () => {
    for (const file of ['components/RoomList/RoomRow.css', 'components/Navigation/Navigation.css']) {
      const css = readFileSync(join(here, file), 'utf8')
      expect(css, file).not.toMatch(/#[0-9a-fA-F]{3,8}\b|rgba?\(|hsla?\(/)
    }
  })
})

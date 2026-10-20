import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const css = readFileSync(resolve(process.cwd(), 'src/index.css'), 'utf8')

function block(selector: string) {
  const start = css.indexOf(selector)
  expect(start).toBeGreaterThanOrEqual(0)
  const open = css.indexOf('{', start)
  return css.slice(open + 1, css.indexOf('}', open))
}

function color(section: string, name: string) {
  const match = new RegExp(`--${name}:\\s*(#[0-9a-fA-F]{6})`).exec(section)
  expect(match, `${name} needs a concrete palette value`).not.toBeNull()
  return match![1]
}

function luminance(hex: string) {
  const channels = [1, 3, 5].map(offset => parseInt(hex.slice(offset, offset + 2), 16) / 255)
  const linear = channels.map(value => value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4)
  return linear[0] * 0.2126 + linear[1] * 0.7152 + linear[2] * 0.0722
}

function ratio(a: string, b: string) {
  const values = [luminance(a), luminance(b)].sort((x, y) => y - x)
  return (values[0] + 0.05) / (values[1] + 0.05)
}

describe('theme palette contrast', () => {
  for (const selector of [':root {', ':root[data-theme="dark"] {']) {
    it(`${selector} keeps text, links, error and focus visible`, () => {
      const section = block(selector)
      const background = color(section, 'color-background')
      const surface = color(section, 'color-surface')
      expect(ratio(color(section, 'color-text'), background)).toBeGreaterThanOrEqual(4.5)
      expect(ratio(color(section, 'color-muted'), surface)).toBeGreaterThanOrEqual(4.5)
      expect(ratio(color(section, 'color-primary'), background)).toBeGreaterThanOrEqual(4.5)
      expect(ratio(color(section, 'color-primary'), surface)).toBeGreaterThanOrEqual(3)
      expect(ratio(color(section, 'color-error'), surface)).toBeGreaterThanOrEqual(4.5)
      expect(ratio(color(section, 'color-text'), color(section, 'color-busy'))).toBeGreaterThanOrEqual(4.5)
    })
  }

  it('keeps regular page colors in semantic tokens', () => {
    const styles = [
      'pages/AdminOccupancyPage.css', 'pages/AdminStatisticsPage.css', 'pages/RolePermissionPage.css',
      'components/RoomDeviceControls/RoomDeviceControlsDialog.css',
      'components/CheckInQrCode/CheckInQrCode.css',
    ]
    for (const path of styles) {
      const pageCss = readFileSync(resolve(process.cwd(), 'src', path), 'utf8')
      expect(pageCss, path).not.toMatch(/#[0-9a-fA-F]{3,8}\b/)
    }
  })
})

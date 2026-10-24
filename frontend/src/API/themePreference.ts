import { z } from 'zod'
import { apiRequest } from './client'

export const themeCodeSchema = z.enum(['LIGHT', 'DARK'])
export type ThemeCode = z.infer<typeof themeCodeSchema>

export const themePreferenceSchema = z.object({
  effectiveTheme: themeCodeSchema,
  selectedTheme: themeCodeSchema.nullable(),
  onboardingPending: z.boolean(),
}).strict()

export type ThemePreference = z.infer<typeof themePreferenceSchema>

const path = '/api/me/theme-preference'

export async function getThemePreference(): Promise<ThemePreference> {
  return themePreferenceSchema.parse(await apiRequest<unknown>(path))
}

export async function saveThemePreference(theme: ThemeCode): Promise<ThemePreference> {
  return themePreferenceSchema.parse(await apiRequest<unknown>(path, {
    method: 'PUT', body: JSON.stringify({ theme }),
  }))
}

export async function skipThemeOnboarding(): Promise<ThemePreference> {
  return themePreferenceSchema.parse(await apiRequest<unknown>(`${path}/skip`, { method: 'POST' }))
}

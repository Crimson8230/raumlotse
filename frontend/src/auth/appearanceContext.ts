import { createContext } from 'react'
import type { ThemeCode, ThemePreference } from '../API/themePreference'

export interface AppearanceContextValue {
  preference: ThemePreference | null
  loading: boolean
  error: string | null
  retry: () => void
  choose: (theme: ThemeCode) => Promise<void>
  skip: () => Promise<void>
}

export const AppearanceContext = createContext<AppearanceContextValue | null>(null)

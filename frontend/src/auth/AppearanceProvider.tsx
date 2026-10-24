import { useEffect, useLayoutEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { useLocation } from 'react-router-dom'
import { getThemePreference, saveThemePreference, skipThemeOnboarding } from '../API/themePreference'
import type { ThemeCode, ThemePreference } from '../API/themePreference'
import { useAuth } from './useAuth'
import { AppearanceContext } from './appearanceContext'

export function AppearanceProvider({ children }: { children: ReactNode }) {
  const { state, user } = useAuth()
  const identity = state === 'authenticated' ? user : null
  const location = useLocation()
  const fixedLightDisplay = /^\/rooms\/[^/]+\/display\/?$/.test(location.pathname)
  const [snapshot, setSnapshot] = useState<{ identity: NonNullable<typeof user>; preference: ThemePreference } | null>(null)
  const [failure, setFailure] = useState<{ identity: NonNullable<typeof user>; message: string } | null>(null)
  const [revision, setRevision] = useState(0)
  const preference = snapshot && snapshot.identity === identity ? snapshot.preference : null
  const error = failure && failure.identity === identity ? failure.message : null

  useEffect(() => {
    if (!identity) return
    let active = true
    getThemePreference().then(
      result => {
        if (active) {
          setSnapshot({ identity, preference: result })
          setFailure(null)
        }
      },
      () => {
        if (active) setFailure({ identity, message: 'Das Farbschema konnte nicht geladen werden.' })
      },
    )
    return () => { active = false }
  }, [identity, revision])

  useLayoutEffect(() => {
    if (identity && !fixedLightDisplay && preference?.effectiveTheme === 'DARK') {
      document.documentElement.dataset.theme = 'dark'
    } else {
      delete document.documentElement.dataset.theme
    }
    return () => { delete document.documentElement.dataset.theme }
  }, [identity, preference?.effectiveTheme, fixedLightDisplay])

  async function choose(theme: ThemeCode) {
    if (!identity) throw new Error('Bitte erneut anmelden.')
    const result = await saveThemePreference(theme)
    setSnapshot(current => current?.identity === identity ? { identity, preference: result } : current)
  }

  async function skip() {
    if (!identity) throw new Error('Bitte erneut anmelden.')
    const result = await skipThemeOnboarding()
    setSnapshot(current => current?.identity === identity ? { identity, preference: result } : current)
  }

  const value = {
    preference, loading: Boolean(identity) && !preference && !error, error,
    retry: () => { setFailure(null); setRevision(current => current + 1) }, choose, skip,
  }

  return <AppearanceContext.Provider value={value}>{children}</AppearanceContext.Provider>
}

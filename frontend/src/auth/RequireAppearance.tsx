import type { ReactNode } from 'react'
import { SkipLink } from '../components/ui'
import AppearanceOnboardingPage from '../pages/AppearanceOnboardingPage'
import { useAppearance } from './useAppearance'

export function RequireAppearance({ children }: { children: ReactNode }) {
  const { preference, loading, error, retry, choose, skip } = useAppearance()
  if (loading) return <main id="inhalt" className="auth-status" aria-live="polite">Farbschema wird geladen…</main>
  if (error) return <main id="inhalt" className="auth-status"><p role="alert">{error}</p><button type="button" onClick={retry}>Erneut versuchen</button></main>
  if (preference?.onboardingPending) {
    return <><SkipLink targetId="inhalt" /><AppearanceOnboardingPage onChoose={choose} onSkip={skip} /></>
  }
  return <>{children}</>
}

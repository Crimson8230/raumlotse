import { useState } from 'react'
import type { ThemeCode } from '../API/themePreference'
import { useAppearance } from '../auth/useAppearance'
import './PersonalSettingsPage.css'

export default function PersonalSettingsPage() {
  const { preference, choose } = useAppearance()
  const effective = preference?.effectiveTheme ?? 'LIGHT'
  const [draft, setDraft] = useState<ThemeCode | null>(null)
  const selected = draft ?? preference?.selectedTheme ?? effective
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  async function save() {
    setBusy(true)
    setError(null)
    setSaved(false)
    try {
      await choose(selected)
      setDraft(null)
      setSaved(true)
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : 'Das Farbschema konnte nicht gespeichert werden.')
    } finally {
      setBusy(false)
    }
  }

  return <main id="inhalt" className="personal-settings">
    <h1>Persönliche Einstellungen</h1>
    <section aria-labelledby="theme-settings-title">
      <h2 id="theme-settings-title">Farbschema</h2>
      <p>Aktuell: {effective === 'DARK' ? 'Dunkel' : 'Hell'}</p>
      {preference?.selectedTheme === null && <p>Sie verwenden das Standardschema.</p>}
      <fieldset className="personal-settings__options">
        <legend>Farbschema auswählen</legend>
        <label><input type="radio" name="theme" checked={selected === 'LIGHT'} disabled={busy}
          onChange={() => setDraft('LIGHT')} /> Hell</label>
        <label><input type="radio" name="theme" checked={selected === 'DARK'} disabled={busy}
          onChange={() => setDraft('DARK')} /> Dunkel</label>
      </fieldset>
      {error && <p role="alert">{error}</p>}
      {saved && <p role="status">Farbschema gespeichert.</p>}
      <button type="button" disabled={busy} onClick={() => void save()}>Farbschema speichern</button>
    </section>
  </main>
}

import { useState } from 'react'
import type { ThemeCode } from '../API/themePreference'
import './AppearanceOnboardingPage.css'

interface Props {
  onChoose: (theme: ThemeCode) => Promise<void>
  onSkip: () => Promise<void>
}

export default function AppearanceOnboardingPage({ onChoose, onSkip }: Props) {
  const [selected, setSelected] = useState<ThemeCode>('LIGHT')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function act(operation: () => Promise<void>) {
    setBusy(true)
    setError(null)
    try { await operation() }
    catch (failure) { setError(failure instanceof Error ? failure.message : 'Die Auswahl konnte nicht gespeichert werden.') }
    finally { setBusy(false) }
  }

  return <main id="inhalt" className="appearance-welcome">
    <h1>Farbschema auswählen</h1>
    <p>Wählen Sie eine Darstellung für die Anwendung. Sie können sie später in Ihren persönlichen Einstellungen ändern.</p>
    <fieldset className="appearance-options">
      <legend>Farbschema</legend>
      {(['LIGHT', 'DARK'] as const).map(code => <label className="appearance-option" key={code}>
        <input type="radio" name="theme" value={code} checked={selected === code}
          onChange={() => setSelected(code)} disabled={busy}
          aria-describedby={`appearance-preview-${code.toLowerCase()}-description`} />
        <span className={`appearance-preview appearance-preview--${code.toLowerCase()}`} aria-hidden="true">
          <span className="appearance-preview__line" /><span className="appearance-preview__button" />
        </span>
        <span>{code === 'LIGHT' ? 'Hell' : 'Dunkel'}</span>
      </label>)}
    </fieldset>
    <span id="appearance-preview-light-description" className="appearance-preview-description">Heller Hintergrund mit dunklem Text und Schaltfläche.</span>
    <span id="appearance-preview-dark-description" className="appearance-preview-description">Dunkler Hintergrund mit hellem Text und Schaltfläche.</span>
    {error && <p role="alert">{error}</p>}
    <div className="appearance-actions">
      <button type="button" disabled={busy} onClick={() => void act(() => onChoose(selected))}>Auswahl speichern</button>
      <button type="button" disabled={busy} onClick={() => void act(onSkip)}>Später auswählen</button>
    </div>
  </main>
}

import type { PathTool } from '../../types/pathNetwork'

interface Props {
  tool: PathTool
  onSelect: (tool: PathTool) => void
}

const TOOLS: { tool: PathTool; label: string; hint: string }[] = [
  { tool: 'view', label: 'Ansehen', hint: 'Wege ansehen, Länge einer Strecke anzeigen' },
  { tool: 'add', label: 'Wegpunkt setzen', hint: 'Auf die Karte klicken, um einen Wegpunkt zu setzen' },
  { tool: 'connect', label: 'Verbinden', hint: 'Zwei Wegpunkte nacheinander anklicken, um sie zu verbinden' },
  { tool: 'move', label: 'Verschieben', hint: 'Wegpunkt ziehen, um ihn zu verschieben' },
  { tool: 'split', label: 'Teilen', hint: 'Auf einen Weg klicken, um dort einen Wegpunkt einzufügen' },
  { tool: 'delete', label: 'Löschen', hint: 'Wegpunkt oder Weg anklicken, um ihn zu löschen' },
  { tool: 'reference', label: 'Referenzstrecke', hint: 'Zwei Punkte der Karte anklicken, deren Abstand bekannt ist' },
]

/** Tool selection for editing the path network; the selected tool decides what a click on the map does. */
export function PathToolbar({ tool, onSelect }: Props) {
  const active = TOOLS.find((candidate) => candidate.tool === tool)
  return (
    <div className="path-toolbar" role="toolbar" aria-label="Werkzeuge für das Wegenetz">
      {TOOLS.map((candidate) => (
        <button
          key={candidate.tool}
          type="button"
          aria-pressed={tool === candidate.tool}
          onClick={() => onSelect(candidate.tool)}
        >
          {candidate.label}
        </button>
      ))}
      <p role="status">{tool === 'attach' ? 'Anbinden: Wegpunkt anklicken.' : active?.hint}</p>
    </div>
  )
}

import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ReferenceSegmentTool } from './ReferenceSegmentTool'
import { sampleNetwork } from './fixtures'

const noScale = sampleNetwork({
  scale: { available: false, pixelsPerMeter: null, widthPx: 200, heightPx: 100 },
  referenceSegment: null,
})
const draft = [{ x: 0.2, y: 0.8 }, { x: 0.6, y: 0.8 }]
const handlers = () => ({ onSave: vi.fn(), onDelete: vi.fn(), onResetDraft: vi.fn() })

describe('ReferenceSegmentTool', () => {
  it('tells everyone that segment lengths are unavailable while the scale is missing', () => {
    render(<ReferenceSegmentTool network={noScale} active={false} draft={[]} canEdit={false} {...handlers()} />)
    expect(screen.getByText(/maßstab fehlt/i)).toBeInTheDocument()
    expect(screen.getByText(/streckenlängen sind nicht verfügbar/i)).toBeInTheDocument()
  })

  it('asks for two points first and offers no length input yet', () => {
    render(<ReferenceSegmentTool network={noScale} active draft={[draft[0]]} canEdit {...handlers()} />)
    expect(screen.getByText(/zwei punkte/i)).toBeInTheDocument()
    expect(screen.queryByLabelText(/reale länge/i)).not.toBeInTheDocument()
  })

  it('saves the drawn endpoints with the entered length, accepting a decimal comma', async () => {
    const user = userEvent.setup()
    const { onSave } = handlers()
    render(<ReferenceSegmentTool network={noScale} active draft={draft} canEdit onSave={onSave} onDelete={vi.fn()} onResetDraft={vi.fn()} />)
    await user.type(screen.getByLabelText(/reale länge in metern/i), '12,5')
    await user.click(screen.getByRole('button', { name: 'Referenzstrecke speichern' }))
    expect(onSave).toHaveBeenCalledWith({ x1: 0.2, y1: 0.8, x2: 0.6, y2: 0.8, lengthMeters: 12.5 })
  })

  it('rejects empty, zero, negative and non-numeric lengths with a clear message', async () => {
    const user = userEvent.setup()
    const { onSave } = handlers()
    render(<ReferenceSegmentTool network={noScale} active draft={draft} canEdit onSave={onSave} onDelete={vi.fn()} onResetDraft={vi.fn()} />)
    const input = screen.getByLabelText(/reale länge in metern/i)
    for (const value of ['', '0', '-3', 'abc']) {
      await user.clear(input)
      if (value) await user.type(input, value)
      await user.click(screen.getByRole('button', { name: 'Referenzstrecke speichern' }))
      expect(screen.getByRole('alert')).toHaveTextContent(/größer als 0/i)
    }
    expect(onSave).not.toHaveBeenCalled()
  })

  it('shows the existing reference segment with its scale and lets editors change or delete it', async () => {
    const user = userEvent.setup()
    const { onSave, onDelete } = handlers()
    render(<ReferenceSegmentTool network={sampleNetwork()} active={false} draft={[]} canEdit onSave={onSave} onDelete={onDelete} onResetDraft={vi.fn()} />)
    expect(screen.getByText(/referenzstrecke: 10 m/i)).toBeInTheDocument()
    expect(screen.getByText(/10 pixel pro meter/i)).toBeInTheDocument()

    const input = screen.getByLabelText(/länge der referenzstrecke/i)
    await user.clear(input)
    await user.type(input, '20')
    await user.click(screen.getByRole('button', { name: 'Länge speichern' }))
    expect(onSave).toHaveBeenCalledWith({ x1: 0.1, y1: 0.9, x2: 0.6, y2: 0.9, lengthMeters: 20 })

    await user.click(screen.getByRole('button', { name: 'Referenzstrecke löschen' }))
    expect(onDelete).toHaveBeenCalled()
  })

  it('is read-only for users without edit rights', () => {
    render(<ReferenceSegmentTool network={sampleNetwork()} active={false} draft={[]} canEdit={false} {...handlers()} />)
    expect(screen.getByText(/referenzstrecke: 10 m/i)).toBeInTheDocument()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })
})

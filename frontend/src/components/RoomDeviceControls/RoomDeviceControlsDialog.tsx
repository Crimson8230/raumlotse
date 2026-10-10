import { useEffect, useRef } from 'react'
import { RoomDeviceControls } from './RoomDeviceControls'
import './RoomDeviceControlsDialog.css'

/** Device control as a popup over the room page (feature 014, FR-021); closes with the button, Escape or the backdrop. */
export function RoomDeviceControlsDialog({ roomId, roomName, onClose }: { roomId: string; roomName: string; onClose: () => void }) {
  const closeButton = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    closeButton.current?.focus()
    const onKeyDown = (event: KeyboardEvent) => { if (event.key === 'Escape') onClose() }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [onClose])

  return (
    <div className="device-dialog-backdrop" onClick={onClose}>
      <div
        className="device-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="device-dialog-title"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="device-dialog-header">
          <h2 id="device-dialog-title">Gerätesteuerung – {roomName}</h2>
          <button type="button" ref={closeButton} onClick={onClose}>Schließen</button>
        </div>
        <RoomDeviceControls roomId={roomId} />
      </div>
    </div>
  )
}

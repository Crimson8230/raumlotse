import { Fan, Lightbulb, LightbulbOff, Lock, LockOpen } from 'lucide-react'
import type { DeviceStates } from '../../types/checkIn'
import './RoomDeviceIcons.css'

/**
 * Read-only state of the simulated room devices (feature 014). Every icon has a text label, so the states stay
 * readable without color and on a black-and-white e-ink panel.
 */
export function RoomDeviceIcons({ devices }: { devices: DeviceStates }) {
  const unlocked = devices.door === 'UNLOCKED'
  return (
    <ul className="room-device-icons" aria-label="Geräte im Raum">
      <li className={devices.lighting ? 'is-on' : 'is-off'}>
        {devices.lighting ? <Lightbulb aria-hidden="true" /> : <LightbulbOff aria-hidden="true" />}
        <span>{devices.lighting ? 'Licht an' : 'Licht aus'}</span>
      </li>
      <li className={devices.ventilation ? 'is-on' : 'is-off'}>
        <Fan aria-hidden="true" />
        <span>{devices.ventilation ? 'Lüftung an' : 'Lüftung aus'}</span>
      </li>
      <li className={unlocked ? 'is-on' : 'is-off'}>
        {unlocked ? <LockOpen aria-hidden="true" /> : <Lock aria-hidden="true" />}
        <span>{unlocked ? 'Tür entriegelt' : 'Tür verriegelt'}</span>
      </li>
    </ul>
  )
}

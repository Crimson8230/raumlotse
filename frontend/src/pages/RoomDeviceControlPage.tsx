import { Link, useParams } from 'react-router-dom'
import { RoomDeviceControls } from '../components/RoomDeviceControls/RoomDeviceControls'

export default function RoomDeviceControlPage() {
  const { roomId } = useParams<{ roomId: string }>()
  return (
    <main>
      <h1>Gerätesteuerung</h1>
      {roomId ? <RoomDeviceControls roomId={roomId} /> : <p role="alert">Raum nicht gefunden.</p>}
      {roomId && <p className="page-back"><Link to={`/rooms/${roomId}`}>Zurück zum Raum</Link></p>}
    </main>
  )
}

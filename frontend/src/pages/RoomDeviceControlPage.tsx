import { useParams } from 'react-router-dom'
import { RoomDeviceControls } from '../components/RoomDeviceControls/RoomDeviceControls'

export default function RoomDeviceControlPage() {
  const { roomId } = useParams<{ roomId: string }>()
  return <main><h1>Room device control</h1>{roomId ? <RoomDeviceControls roomId={roomId} /> : <p role="alert">Room not found.</p>}</main>
}

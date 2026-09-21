import { Route, Routes } from 'react-router-dom'
import { Navigation } from './components/Navigation/Navigation'
import HomePage from './pages/HomePage'
import LocationCatalogPage from './pages/LocationCatalogPage'
import RoomListPage from './pages/RoomListPage'
import RoomFormPage from './pages/RoomFormPage'
import RoomDetailPage from './pages/RoomDetailPage'
import RoomDisplayPage from './pages/RoomDisplayPage'

function App() {
  return (
    <>
      <Navigation />
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/locations" element={<LocationCatalogPage />} />
        <Route path="/rooms" element={<RoomListPage />} />
        <Route path="/rooms/new" element={<RoomFormPage />} />
        <Route path="/rooms/:roomId" element={<RoomDetailPage />} />
        <Route path="/rooms/:roomId/display" element={<RoomDisplayPage />} />
        <Route path="/rooms/:roomId/edit" element={<RoomFormPage />} />
      </Routes>
    </>
  )
}

export default App

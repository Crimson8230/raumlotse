import { Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthProvider'
import { RequireAuth } from './auth/RequireAuth'
import LoginPage from './pages/LoginPage'
import HomePage from './pages/HomePage'
import LocationCatalogPage from './pages/LocationCatalogPage'
import RoomListPage from './pages/RoomListPage'
import RoomFormPage from './pages/RoomFormPage'
import RoomDetailPage from './pages/RoomDetailPage'
import RoomDisplayPage from './pages/RoomDisplayPage'
import { RequireAdmin } from './auth/RequireAdmin'
import UserRoleListPage from './pages/UserRoleListPage'
import UserRolePage from './pages/UserRolePage'
import RoomDeviceControlPage from './pages/RoomDeviceControlPage'
import AdminStatisticsPage from './pages/AdminStatisticsPage'
import MapPage from './pages/MapPage'

function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/login" element={<LoginPage />} />

        <Route element={<RequireAuth />}>
          <Route element={<RequireAdmin />}>
            <Route path="/admin/users" element={<UserRoleListPage />} />
            <Route path="/admin/users/:userId/roles" element={<UserRolePage />} />
            <Route path="/admin/statistics" element={<AdminStatisticsPage />} />
          </Route>

          <Route path="/" element={<HomePage />} />
          <Route path="/locations" element={<LocationCatalogPage />} />
          <Route path="/maps" element={<MapPage />} />
          <Route path="/maps/:mapId" element={<MapPage />} />
          <Route path="/rooms" element={<RoomListPage />} />
          <Route path="/rooms/new" element={<RoomFormPage />} />
          <Route path="/rooms/:roomId" element={<RoomDetailPage />} />
          <Route path="/rooms/:roomId/display" element={<RoomDisplayPage />} />
          <Route path="/rooms/:roomId/control" element={<RoomDeviceControlPage />} />
          <Route path="/rooms/:roomId/edit" element={<RoomFormPage />} />
        </Route>
      </Routes>
    </AuthProvider>
  )
}

export default App

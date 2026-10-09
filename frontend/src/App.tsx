import { Navigate, Route, Routes, useParams } from 'react-router-dom'
import { AuthProvider } from './auth/AuthProvider'
import { RequireAuth } from './auth/RequireAuth'
import LoginPage from './pages/LoginPage'
import HomePage from './pages/HomePage'
import LocationCatalogPage from './pages/LocationCatalogPage'
import RoomListPage from './pages/RoomListPage'
import RoomFormPage from './pages/RoomFormPage'
import RoomDetailPage from './pages/RoomDetailPage'
import RoomDisplayPage from './pages/RoomDisplayPage'
import { RequireAdminMode } from './auth/RequireAdminMode'
import UserRoleListPage from './pages/UserRoleListPage'
import UserRolePage from './pages/UserRolePage'
import RoomDeviceControlPage from './pages/RoomDeviceControlPage'
import MapPage from './pages/MapPage'
import AdminStatisticsPage from './pages/AdminStatisticsPage'
import RolePermissionPage from './pages/RolePermissionPage'
import { RequireRoleManagement } from './auth/RequireRoleManagement'
import { RequirePermission } from './auth/RequirePermission'

function RoomEditRedirect() {
  const { roomId } = useParams()
  return <Navigate to={`/admin/rooms/${roomId}/edit`} replace />
}

/**
 * Two address spaces (feature 013): everything outside `/admin` is the user view for any signed-in user,
 * everything under `/admin` is administration and requires administration mode.
 */
function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/login" element={<LoginPage />} />

        <Route element={<RequireAuth />}>
          <Route path="/" element={<HomePage />} />
          <Route element={<RequirePermission any={['READ']} />}>
            <Route path="/maps" element={<MapPage />} />
            <Route path="/maps/:mapId" element={<MapPage />} />
            <Route path="/rooms" element={<RoomListPage />} />
            <Route path="/rooms/:roomId" element={<RoomDetailPage />} />
            <Route path="/rooms/:roomId/display" element={<RoomDisplayPage />} />
          </Route>
          <Route element={<RequirePermission any={['OWN_ACTIVE_DEVICE_CONTROL']} />}>
            <Route path="/rooms/:roomId/control" element={<RoomDeviceControlPage />} />
          </Route>

          <Route path="/admin" element={<RequireAdminMode />}>
            <Route element={<RequirePermission any={['BUILDING_MANAGE', 'FLOOR_MANAGE', 'EQUIPMENT_TYPE_MANAGE']} />}>
              <Route path="locations" element={<LocationCatalogPage />} />
            </Route>
            <Route element={<RequirePermission any={['MAP_MANAGE', 'ROOM_PLACEMENT_MANAGE', 'CONNECTION_MANAGE']} />}>
              <Route path="maps" element={<MapPage editable />} />
              <Route path="maps/:mapId" element={<MapPage editable />} />
            </Route>
            <Route element={<RequirePermission any={['ROOM_MANAGE']} />}>
              <Route path="rooms/new" element={<RoomFormPage />} />
              <Route path="rooms/:roomId/edit" element={<RoomFormPage />} />
            </Route>
            <Route element={<RequirePermission any={['STATISTICS_READ']} />}>
              <Route path="statistics" element={<AdminStatisticsPage />} />
            </Route>
            <Route element={<RequireRoleManagement />}>
              <Route path="users" element={<UserRoleListPage />} />
              <Route path="users/:userId/roles" element={<UserRolePage />} />
              <Route path="roles" element={<RolePermissionPage />} />
            </Route>
          </Route>

          {/* Addresses before feature 013 */}
          <Route path="/locations" element={<Navigate to="/admin/locations" replace />} />
          <Route path="/rooms/new" element={<Navigate to="/admin/rooms/new" replace />} />
          <Route path="/rooms/:roomId/edit" element={<RoomEditRedirect />} />
        </Route>
      </Routes>
    </AuthProvider>
  )
}

export default App

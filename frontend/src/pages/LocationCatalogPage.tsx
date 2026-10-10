import { BuildingCatalog } from '../components/BuildingCatalog/BuildingCatalog'
import { EquipmentCatalog } from '../components/EquipmentCatalog/EquipmentCatalog'
import { useCurrentRoles } from '../auth/useCurrentRoles'

export default function LocationCatalogPage() {
  const { permissions } = useCurrentRoles()
  const buildings = permissions.includes('BUILDING_MANAGE')
  const floors = permissions.includes('FLOOR_MANAGE')
  const equipment = permissions.includes('EQUIPMENT_TYPE_MANAGE')
  return (
    <main>
      <h1>Standorte</h1>
      <p>Gebäude, Stockwerke &amp; Ausstattung verwalten</p>
      {(buildings || floors) && <BuildingCatalog canManageBuildings={buildings} canManageFloors={floors} />}
      {equipment && <EquipmentCatalog />}
    </main>
  )
}

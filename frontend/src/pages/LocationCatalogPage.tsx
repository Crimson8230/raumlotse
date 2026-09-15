import { BuildingCatalog } from '../components/BuildingCatalog/BuildingCatalog'
import { EquipmentCatalog } from '../components/EquipmentCatalog/EquipmentCatalog'

export default function LocationCatalogPage() {
  return (
    <div>
      <h1>Buildings, Floors &amp; Equipment</h1>
      <BuildingCatalog />
      <EquipmentCatalog />
    </div>
  )
}

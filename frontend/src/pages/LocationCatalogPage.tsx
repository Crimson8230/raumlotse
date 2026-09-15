import { BuildingCatalog } from '../components/BuildingCatalog/BuildingCatalog'
import { EquipmentCatalog } from '../components/EquipmentCatalog/EquipmentCatalog'

export default function LocationCatalogPage() {
  return (
    <main>
      <h1>Standorte</h1>
      <p>Gebäude, Stockwerke &amp; Ausstattung verwalten</p>
      <BuildingCatalog />
      <EquipmentCatalog />
    </main>
  )
}

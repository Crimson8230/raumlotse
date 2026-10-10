import { render, screen } from '@testing-library/react'
import { beforeEach, expect, it, vi } from 'vitest'
import LocationCatalogPage from './LocationCatalogPage'

let mockPermissions: string[] = []
vi.mock('../auth/useCurrentRoles', () => ({ useCurrentRoles: () => ({ permissions: mockPermissions }) }))
vi.mock('../components/BuildingCatalog/BuildingCatalog', () => ({
  BuildingCatalog: ({ canManageBuildings, canManageFloors }: {
    canManageBuildings: boolean; canManageFloors: boolean
  }) => <div data-testid="building-catalog">building={String(canManageBuildings)} floor={String(canManageFloors)}</div>,
}))
vi.mock('../components/EquipmentCatalog/EquipmentCatalog', () => ({
  EquipmentCatalog: () => <div data-testid="equipment-catalog" />,
}))

beforeEach(() => { mockPermissions = [] })

it('shows only the floor portion when FLOOR_MANAGE is the sole management right', () => {
  mockPermissions = ['READ', 'FLOOR_MANAGE']
  render(<LocationCatalogPage />)
  expect(screen.getByTestId('building-catalog')).toHaveTextContent('building=false floor=true')
  expect(screen.queryByTestId('equipment-catalog')).not.toBeInTheDocument()
})

it('shows equipment without building or floor controls when only EQUIPMENT_TYPE_MANAGE is granted', () => {
  mockPermissions = ['READ', 'EQUIPMENT_TYPE_MANAGE']
  render(<LocationCatalogPage />)
  expect(screen.getByTestId('equipment-catalog')).toBeVisible()
  expect(screen.queryByTestId('building-catalog')).not.toBeInTheDocument()
})

it('renders no management component after rights are lost', () => {
  render(<LocationCatalogPage />)
  expect(screen.queryByTestId('equipment-catalog')).not.toBeInTheDocument()
  expect(screen.queryByTestId('building-catalog')).not.toBeInTheDocument()
})

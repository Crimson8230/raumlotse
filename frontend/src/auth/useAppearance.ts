import { useContext } from 'react'
import { AppearanceContext } from './appearanceContext'

export function useAppearance() {
  const value = useContext(AppearanceContext)
  if (!value) throw new Error('useAppearance requires AppearanceProvider')
  return value
}

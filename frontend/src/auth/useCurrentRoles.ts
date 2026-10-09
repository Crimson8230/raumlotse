import { useEffect, useState } from 'react'
import { useLocation } from 'react-router-dom'
import { getCurrentRoles } from '../API/userRoles'
import type { RoleCode } from '../types/userRole'
import type { PermissionCode } from '../types/permission'

export function useCurrentRoles(enabled = true) {
  const { key } = useLocation()
  const [result, setResult] = useState<{ key: string; roles: RoleCode[]; permissions: PermissionCode[];
    canUseAdminMode: boolean; ready: boolean; adminMode: boolean; failed: boolean } | null>(null)
  useEffect(() => {
    if (!enabled) return
    let active = true
    let sequence = 0
    const load = async () => {
      const current = ++sequence
      // A revoked right must stop exposing actions while the fresh snapshot is in flight.
      setResult(null)
      try {
        const data = await getCurrentRoles()
        if (active && current === sequence) setResult({ key, ...data,
          permissions: Array.isArray(data.permissions) ? data.permissions : [],
          canUseAdminMode: data.canUseAdminMode === true,
          adminMode: data.adminMode === true, failed: false })
      } catch {
        if (active && current === sequence) setResult({ key, roles: [], permissions: [], canUseAdminMode: false,
          ready: false, adminMode: false, failed: true })
      }
    }
    void load()
    window.addEventListener('focus', load)
    window.addEventListener('raumlotse:roles-changed', load)
    return () => {
      active = false
      window.removeEventListener('focus', load)
      window.removeEventListener('raumlotse:roles-changed', load)
    }
  }, [key, enabled])
  return {
    loading: enabled && result?.key !== key,
    admin: enabled && result?.key === key && result.ready && result.roles.includes('ADMIN'),
    adminMode: enabled && result?.key === key && result.ready && result.canUseAdminMode && result.adminMode,
    canUseAdminMode: Boolean(enabled && result?.key === key && result.ready && result.canUseAdminMode),
    permissions: enabled && result?.key === key && result.ready ? result.permissions : [],
    failed: enabled && result?.key === key && result.failed,
  }
}


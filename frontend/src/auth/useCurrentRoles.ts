import { useEffect, useState } from 'react'
import { useLocation } from 'react-router-dom'
import { getCurrentRoles } from '../API/userRoles'
import type { RoleCode } from '../types/userRole'
import type { PermissionCode } from '../types/permission'

export function useCurrentRoles(enabled = true) {
  const { key } = useLocation()
  const [result, setResult] = useState<{ key: string; roles: RoleCode[]; permissions: PermissionCode[];
    canUseAdminMode: boolean; ready: boolean; adminMode: boolean; failed: boolean; pending?: boolean } | null>(null)
  useEffect(() => {
    if (!enabled) return
    let active = true
    let sequence = 0
    const load = async () => {
      const current = ++sequence
      // A revoked right must stop exposing actions while the fresh snapshot is in flight. The previous snapshot is
      // kept only as a marker, so route guards can keep an already granted page mounted (scroll position, state).
      setResult(previous => previous?.key === key && previous.ready && !previous.failed
        ? { ...previous, pending: true } : null)
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
  const settled = result?.key === key && !result.pending ? result : null
  return {
    loading: enabled && settled === null,
    /** A re-check (focus, role change) of a snapshot that was granted for this route is in flight. */
    refreshing: enabled && result?.key === key && result.pending === true,
    admin: enabled && settled !== null && settled.ready && settled.roles.includes('ADMIN'),
    adminMode: enabled && settled !== null && settled.ready && settled.canUseAdminMode && settled.adminMode,
    canUseAdminMode: Boolean(enabled && settled !== null && settled.ready && settled.canUseAdminMode),
    permissions: enabled && settled !== null && settled.ready ? settled.permissions : [],
    failed: enabled && settled !== null && settled.failed,
  }
}


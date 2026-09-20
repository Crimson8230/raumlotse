import { useEffect, useState } from 'react'
import { useLocation } from 'react-router-dom'
import { getCurrentRoles } from '../API/userRoles'
import type { RoleCode } from '../types/userRole'

export function useCurrentRoles(enabled = true) {
  const { key } = useLocation()
  const [result, setResult] = useState<{ key: string; roles: RoleCode[]; ready: boolean; failed: boolean } | null>(null)
  useEffect(() => {
    if (!enabled) return
    let active = true
    let sequence = 0
    const load = async () => {
      const current = ++sequence
      try {
        const data = await getCurrentRoles()
        if (active && current === sequence) setResult({ key, ...data, failed: false })
      } catch {
        if (active && current === sequence) setResult({ key, roles: [], ready: false, failed: true })
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
    failed: enabled && result?.key === key && result.failed,
  }
}


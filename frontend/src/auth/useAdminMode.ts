import { useCallback } from 'react'
import { setAdminMode } from '../API/userRoles'
import { useCurrentRoles } from './useCurrentRoles'

/**
 * Administration mode (feature 013). `admin` says whether the user may switch it on, `adminMode` whether administration
 * options are currently shown. The server keeps the flag per session; every user of this hook re-reads it when the
 * `raumlotse:roles-changed` event fires.
 */
export function useAdminMode(enabled = true) {
  const roles = useCurrentRoles(enabled)
  const setMode = useCallback(async (value: boolean) => {
    await setAdminMode(value)
    window.dispatchEvent(new Event('raumlotse:roles-changed'))
  }, [])
  return { loading: roles.loading, failed: roles.failed, admin: roles.admin, adminMode: roles.adminMode, setMode }
}

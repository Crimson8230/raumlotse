import { useCallback, useEffect, useState } from 'react'
import { formatApiError } from '../../API/client'
import { getNetworkWide } from '../../API/pathNetwork'
import type { ConnectionGroup, MapNetwork } from '../../types/pathNetwork'

/** Loads the path network of one map (and the connection groups) while `enabled`; `reload` fetches it again. */
export function usePathNetwork(mapId: string | undefined, enabled: boolean) {
  const [loaded, setLoaded] = useState<{ mapId: string; network: MapNetwork; groups: ConnectionGroup[] }>()
  const [error, setError] = useState<string>()
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    if (!enabled || !mapId) return
    let active = true
    getNetworkWide()
      .then((result) => {
        if (!active) return
        const network = result.maps.find((candidate) => candidate.mapId === mapId)
        setError(undefined)
        setLoaded(network ? { mapId, network, groups: result.connectionGroups } : undefined)
      })
      .catch((err: unknown) => active && setError(formatApiError(err)))
    return () => {
      active = false
    }
  }, [mapId, enabled, reloadKey])

  const reload = useCallback(() => setReloadKey((key) => key + 1), [])
  // Only the network that belongs to the current selection is returned; a stale one is dropped without an effect.
  const current = enabled && loaded?.mapId === mapId ? loaded : undefined
  return { network: current?.network, groups: current?.groups, error, reload }
}

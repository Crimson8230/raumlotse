import { useCallback, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { ApiError } from '../API/client'
import { clearCsrfToken } from '../API/client'
import { authApi } from '../API/auth'
import type { AuthenticatedUser, Credentials } from '../API/auth'

import { AuthContext, type AuthState } from './authContext'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>('loading')
  const [user, setUser] = useState<AuthenticatedUser | null>(null)

  useEffect(() => {
    let active = true
    void (async () => {
      try {
        await authApi.refreshCsrfToken()
        const identity = await authApi.me()
        if (active) {
          setUser(identity)
          setState('authenticated')
        }
      } catch (error) {
        if (!active) return
        if (error instanceof ApiError && error.status === 401) {
          setUser(null)
          setState('anonymous')
        } else {
          setState('unavailable')
        }
      }
    })()
    return () => { active = false }
  }, [])

  useEffect(() => {
    const expire = () => {
      clearCsrfToken()
      setUser(null)
      setState('anonymous')
    }
    window.addEventListener('raumlotse:auth-expired', expire)
    return () => window.removeEventListener('raumlotse:auth-expired', expire)
  }, [])

  const login = useCallback(async (credentials: Credentials) => {
    let acceptedIdentity: AuthenticatedUser | null = null
    try {
      const identity = await authApi.login(credentials)
      acceptedIdentity = identity
      setUser(identity)
      setState('finishing-login')
      await authApi.refreshCsrfToken()
      setState('authenticated')
    } catch (error) {
      if (acceptedIdentity) {
        setUser(acceptedIdentity)
        setState('unavailable')
      } else {
        setUser(null)
        setState(error instanceof ApiError && error.status < 500 ? 'anonymous' : 'unavailable')
      }
      throw error
    }
  }, [])

  const retryAvailability = useCallback(async () => {
    try {
      await authApi.refreshCsrfToken()
      const identity = user ?? await authApi.me()
      setUser(identity)
      setState('authenticated')
    } catch (error) {
      setState('unavailable')
      throw error
    }
  }, [user])

  const value = useMemo(() => ({ state, user, login, retryAvailability }), [state, user, login, retryAvailability])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

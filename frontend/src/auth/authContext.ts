import { createContext } from 'react'
import type { AuthenticatedUser, Credentials } from '../API/auth'

export type AuthState = 'loading' | 'finishing-login' | 'authenticated' | 'anonymous' | 'unavailable'

export interface AuthContextValue {
  state: AuthState
  user: AuthenticatedUser | null
  login: (credentials: Credentials) => Promise<void>
  retryAvailability: () => Promise<void>
}

export const AuthContext = createContext<AuthContextValue | null>(null)

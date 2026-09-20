import { apiRequest, refreshCsrfToken } from './client'

export interface AuthenticatedUser {
  userId: string
  displayName: string
}

export interface Credentials {
  email: string
  password: string
}

export const authApi = {
  me: () => apiRequest<AuthenticatedUser>('/api/auth/me'),
  login: (credentials: Credentials) => apiRequest<AuthenticatedUser>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify(credentials),
  }),
  refreshCsrfToken,
}

import { http } from './http'
import type { Result } from './types'

export interface SessionInfo {
  jti: string
  userId: number
  username: string
  ip: string
  userAgent: string
  deviceType: string
  loginAt: string
  expiresAt: string
}

export const sessionApi = {
  mySessions(): Promise<Result<SessionInfo[]>> {
    return http.get('/sys/auth/sessions')
  },

  revokeMySession(jti: string): Promise<Result<void>> {
    return http.post(`/sys/auth/sessions/${jti}/revoke`)
  },

  userSessions(userId: number): Promise<Result<SessionInfo[]>> {
    return http.get(`/sys/user/${userId}/sessions`)
  },

  revokeUserSession(userId: number, jti: string): Promise<Result<void>> {
    return http.post(`/sys/user/${userId}/sessions/${jti}/revoke`)
  },
}

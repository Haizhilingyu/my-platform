import { http } from '@/modules/sys/api/http'
import type { Result, PageResult } from '@/modules/sys/api/types'

export interface OpenAppClientVO {
  id: number
  clientId: string
  clientName: string | null
  redirectUris: string[]
  postLogoutRedirectUris: string[]
  scopes: string[]
  grantTypes: string[]
  enabled: boolean
  createdAt: string
}

export interface OpenAppClientQuery {
  keyword?: string
  enabled?: boolean
  pageNum: number
  pageSize: number
}

export interface OpenAppClientCreateDTO {
  clientName: string
  redirectUris: string[]
  postLogoutRedirectUris: string[]
  scopes: string[]
  grantTypes: string[]
}

export interface OpenAppClientUpdateDTO {
  clientName: string
  redirectUris: string[]
  postLogoutRedirectUris: string[]
  scopes: string[]
  grantTypes: string[]
  enabled: boolean
}

export interface OpenAppSecretResult {
  id: number
  clientId: string
  clientSecret: string
}

export const openAppApi = {
  list(query: OpenAppClientQuery): Promise<Result<PageResult<OpenAppClientVO>>> {
    return http.get('/sys/openapp/clients', { params: query })
  },

  get(id: number): Promise<Result<OpenAppClientVO>> {
    return http.get(`/sys/openapp/clients/${id}`)
  },

  create(data: OpenAppClientCreateDTO): Promise<Result<OpenAppSecretResult>> {
    return http.post('/sys/openapp/clients', data)
  },

  update(id: number, data: OpenAppClientUpdateDTO): Promise<Result<void>> {
    return http.put(`/sys/openapp/clients/${id}`, data)
  },

  delete(id: number): Promise<Result<void>> {
    return http.delete(`/sys/openapp/clients/${id}`)
  },

  resetSecret(id: number): Promise<Result<OpenAppSecretResult>> {
    return http.post(`/sys/openapp/clients/${id}/reset-secret`)
  },
}

import { http } from './http'
import type { Result, SysConfig } from './types'

export interface ConfigDTO {
  id?: number
  configKey: string
  configValue?: string
  configType?: string
  description?: string
  category?: string
}

export const configApi = {
  list(category?: string): Promise<Result<SysConfig[]>> {
    return http.get('/sys/config', { params: { category } })
  },

  getByKey(key: string): Promise<Result<SysConfig>> {
    return http.get(`/sys/config/${key}`)
  },

  create(data: ConfigDTO): Promise<Result<number>> {
    return http.post('/sys/config', data)
  },

  update(id: number, data: ConfigDTO): Promise<Result<void>> {
    return http.put(`/sys/config/${id}`, data)
  },

  batchUpdate(configs: ConfigDTO[]): Promise<Result<void>> {
    return http.put('/sys/config/batch', configs)
  },
}

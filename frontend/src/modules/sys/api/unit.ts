import { http } from './http'
import type { Result, UnitTreeNode, SysUnit } from './types'

export interface UnitDTO {
  parentId?: number
  unitCode: string
  unitName: string
  sort?: number
  status?: number
  remark?: string
}

export const unitApi = {
  tree(): Promise<Result<UnitTreeNode[]>> {
    return http.get('/sys/unit/tree')
  },

  get(id: number): Promise<Result<SysUnit>> {
    return http.get(`/sys/unit/${id}`)
  },

  create(data: UnitDTO): Promise<Result<number>> {
    return http.post('/sys/unit', data)
  },

  update(id: number, data: UnitDTO): Promise<Result<void>> {
    return http.put(`/sys/unit/${id}`, data)
  },

  delete(id: number): Promise<Result<void>> {
    return http.delete(`/sys/unit/${id}`)
  },
}

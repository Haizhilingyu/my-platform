import { http } from './http'
import type { Result, SysRole } from './types'

export interface RoleDTO {
  roleCode: string
  roleName: string
  dataScope?: string
  status?: number
  remark?: string
}

export const roleApi = {
  list(): Promise<Result<SysRole[]>> {
    return http.get('/sys/role')
  },

  get(id: number): Promise<Result<SysRole>> {
    return http.get(`/sys/role/${id}`)
  },

  create(data: RoleDTO): Promise<Result<number>> {
    return http.post('/sys/role', data)
  },

  update(id: number, data: RoleDTO): Promise<Result<void>> {
    return http.put(`/sys/role/${id}`, data)
  },

  delete(id: number): Promise<Result<void>> {
    return http.delete(`/sys/role/${id}`)
  },

  assignMenus(id: number, menuIds: number[]): Promise<Result<void>> {
    return http.post(`/sys/role/${id}/menus`, menuIds)
  },

  getRoleMenus(id: number): Promise<Result<number[]>> {
    return http.get(`/sys/role/${id}/menus`)
  },

  saveCustomUnits(id: number, unitIds: number[]): Promise<Result<void>> {
    return http.put(`/sys/role/${id}/data-scope`, unitIds)
  },

  getCustomUnits(id: number): Promise<Result<number[]>> {
    return http.get(`/sys/role/${id}/data-scope`)
  },
}

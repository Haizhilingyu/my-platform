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

  /**
   * 保存角色的自定义数据范围（dataScope=CUSTOM 时生效）。
   *
   * 注意：后端 RoleController 暂未暴露 `PUT /sys/role/{id}/data-scope` 端点，
   * 调用方需以 best-effort 方式处理失败（见 T24 known limitation）。
   */
  saveCustomUnits(id: number, unitIds: number[]): Promise<Result<void>> {
    return http.put(`/sys/role/${id}/data-scope`, unitIds)
  },

  /**
   * 查询角色已配置的自定义单位 ID。
   *
   * 注意：后端暂未暴露对应 GET 端点，调用方需以 best-effort 方式处理失败。
   */
  getCustomUnits(id: number): Promise<Result<number[]>> {
    return http.get(`/sys/role/${id}/data-scope`)
  },
}

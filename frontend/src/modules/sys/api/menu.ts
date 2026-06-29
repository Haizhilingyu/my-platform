import { http } from './http'
import type { Result, MenuTreeNode, SysMenu } from './types'

export interface MenuDTO {
  parentId?: number
  menuName: string
  menuType: string
  path?: string
  component?: string
  permission?: string
  icon?: string
  sort?: number
  visible?: number
  status?: number
}

export const menuApi = {
  tree(): Promise<Result<MenuTreeNode[]>> {
    return http.get('/sys/menu/tree')
  },

  get(id: number): Promise<Result<SysMenu>> {
    return http.get(`/sys/menu/${id}`)
  },

  create(data: MenuDTO): Promise<Result<number>> {
    return http.post('/sys/menu', data)
  },

  update(id: number, data: MenuDTO): Promise<Result<void>> {
    return http.put(`/sys/menu/${id}`, data)
  },

  delete(id: number): Promise<Result<void>> {
    return http.delete(`/sys/menu/${id}`)
  },
}

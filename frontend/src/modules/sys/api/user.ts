import { http } from './http'
import type { Result, PageResult, UserVO } from './types'

export interface UserQuery {
  keyword?: string
  unitId?: number
  status?: number
  pageNum: number
  pageSize: number
}

export interface UserCreateDTO {
  username: string
  password: string
  realName?: string
  email?: string
  phone?: string
  unitId?: number
  roleIds?: number[]
}

export interface UserUpdateDTO {
  realName?: string
  email?: string
  phone?: string
  unitId?: number
  status?: number
  remark?: string
}

export const userApi = {
  list(query: UserQuery): Promise<Result<PageResult<UserVO>>> {
    return http.get('/sys/user', { params: query })
  },

  get(id: number): Promise<Result<UserVO>> {
    return http.get(`/sys/user/${id}`)
  },

  create(data: UserCreateDTO): Promise<Result<number>> {
    return http.post('/sys/user', data)
  },

  update(id: number, data: UserUpdateDTO): Promise<Result<void>> {
    return http.put(`/sys/user/${id}`, data)
  },

  delete(id: number): Promise<Result<void>> {
    return http.delete(`/sys/user/${id}`)
  },

  assignRoles(id: number, roleIds: number[]): Promise<Result<void>> {
    return http.post(`/sys/user/${id}/roles`, roleIds)
  },

  getUserRoles(id: number): Promise<Result<number[]>> {
    return http.get(`/sys/user/${id}/roles`)
  },

  resetPassword(id: number, newPassword: string): Promise<Result<void>> {
    return http.post(`/sys/user/${id}/reset-password`, null, { params: { newPassword } })
  },

  unlock(id: number): Promise<Result<void>> {
    return http.post(`/sys/user/${id}/unlock`)
  },
}

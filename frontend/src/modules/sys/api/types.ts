export interface UserVO {
  id: number
  username: string
  realName: string
  email: string
  phone: string
  unitId: number | null
  unitName: string | null
  avatar: string | null
  status: number
  remark: string | null
  createdAt: string
}

export interface SysRole {
  id: number
  roleCode: string
  roleName: string
  dataScope: string
  status: number
  remark: string | null
  createdAt: string
}

export interface SysMenu {
  id: number
  parentId: number | null
  menuName: string
  menuType: string
  path: string | null
  component: string | null
  permission: string | null
  icon: string | null
  sort: number
  visible: number
  status: number
}

export interface MenuTreeNode extends SysMenu {
  children: MenuTreeNode[]
}

export interface SysUnit {
  id: number
  parentId: number | null
  unitCode: string
  unitName: string
  sort: number
  status: number
  remark: string | null
}

export interface UnitTreeNode extends SysUnit {
  children: UnitTreeNode[]
}

export interface SysConfig {
  id: number
  configKey: string
  configValue: string | null
  configType: string
  description: string | null
  category: string
}

export interface PageResult<T> {
  list: T[]
  total: number
  pageNum: number
  pageSize: number
}

export interface Result<T> {
  code: number
  message: string
  data: T
}

export interface LoginVO {
  token: string
  tokenType: string
  user: UserVO
}

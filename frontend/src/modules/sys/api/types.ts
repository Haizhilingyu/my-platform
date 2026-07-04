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
  /** 锁定状态：后端 UserVO 暂未填充，需 LoginSecurityService.isLocked 接入。 */
  locked?: boolean
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

/**
 * 登录请求载荷（与后端 LoginRequest 对齐）。
 * method 缺省时后端默认 "password"；开启验证码时 captchaId/captchaCode 必填。
 */
export interface LoginRequest {
  method?: string
  username: string
  password: string
  captchaId?: string | null
  captchaCode?: string | null
  attributes?: Record<string, unknown> | null
}

/** 登录方式描述符；前端按 order 升序渲染 Tab。 */
export interface LoginMethodDescriptor {
  method: string
  label: string
  icon: string | null
  order: number
}

/** 图形验证码响应；image 为 data URI，captchaId 需原样回传校验。 */
export interface CaptchaResult {
  captchaId: string
  image: string
}

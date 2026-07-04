import { http } from './http'
import type { Result, PageResult } from './types'

export interface AuditLogVO {
  id: number
  actor: string
  actorType: string | null
  action: string
  targetType: string | null
  targetId: string | null
  ip: string | null
  userAgent: string | null
  /** 已脱敏的方法参数，类型为 JSON 文本字符串而非对象。 */
  params: string | null
  result: 'success' | 'fail'
  errorMsg: string | null
  createdAt: string
}

export interface AuditLogQuery {
  actor?: string
  action?: string
  result?: 'success' | 'fail'
  targetType?: string
  targetId?: string
  startTime?: string
  endTime?: string
  pageNum: number
  pageSize: number
}

export const auditApi = {
  list(query: AuditLogQuery): Promise<Result<PageResult<AuditLogVO>>> {
    return http.get('/sys/audit/logs', { params: query })
  },
}

import { http } from '@/modules/sys/api/http'
import type { Result, PageResult } from '@/modules/sys/api/types'

// notify 模块尚未提供 GET 收件箱端点（T15 仅有 /sys/notify/publish），
// 此处按约定的 RESTful 形态封装，后端补齐前调用会 404，调用方需 catch。
export type NotifyLevel = 'URGENT' | 'IMPORTANT' | 'NORMAL'

export interface NotifyInboxVO {
  id: number
  messageId: number
  seq: number
  title: string
  content: string
  level: NotifyLevel
  businessType: string | null
  readStatus: boolean
  createdAt: string
}

export interface NotifyInboxQuery {
  level?: NotifyLevel
  readStatus?: boolean
  keyword?: string
  pageNum: number
  pageSize: number
}

export const notifyApi = {
  inbox(query: NotifyInboxQuery): Promise<Result<PageResult<NotifyInboxVO>>> {
    return http.get('/sys/notify/inbox', { params: query })
  },

  markRead(id: number): Promise<Result<void>> {
    return http.put(`/sys/notify/inbox/${id}/read`)
  },

  batchMarkRead(ids: number[]): Promise<Result<void>> {
    return http.put('/sys/notify/inbox/batch-read', { ids })
  },

  unreadCount(): Promise<Result<number>> {
    return http.get('/sys/notify/inbox/unread-count')
  },
}

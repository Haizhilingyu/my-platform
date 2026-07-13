import { http } from '@/modules/sys/api/http'
import type { Result, PageResult } from '@/modules/sys/api/types'

export type AppLocale = 'zh-CN' | 'en'

export interface I18nMessageVO {
  id: number
  messageKey: string
  locale: AppLocale
  module: string
  value: string
  description?: string
  updatedAt: string
}

export interface I18nMessageUpdateDTO {
  value: string
}

export interface I18nMessageImportItem {
  messageKey: string
  value: string
}

export interface I18nMessageImportDTO {
  locale: AppLocale
  items: I18nMessageImportItem[]
}

export interface I18nMessageQuery {
  locale?: AppLocale
  module?: string
  keyLike?: string
  pageNum?: number
  pageSize?: number
}

export const i18nApi = {
  list(query: I18nMessageQuery): Promise<Result<PageResult<I18nMessageVO>>> {
    return http.get('/i18n/messages', { params: query })
  },
  fetchAll(locale: AppLocale): Promise<Result<Record<string, string>>> {
    return http.get('/i18n/messages/all', { params: { locale } })
  },
  update(id: number, dto: I18nMessageUpdateDTO): Promise<Result<I18nMessageVO>> {
    return http.put(`/i18n/messages/${id}`, dto)
  },
  importJson(dto: I18nMessageImportDTO): Promise<Result<number>> {
    return http.post('/i18n/messages/import', dto)
  },
  importXlsx(file: File, locale: AppLocale): Promise<Result<number>> {
    const form = new FormData()
    form.append('file', file)
    form.append('locale', locale)
    return http.post('/i18n/messages/import', form, {
      params: { format: 'xlsx' },
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
  exportJson(locale: AppLocale): Promise<Blob> {
    return http.get('/i18n/messages/export', {
      params: { locale, format: 'json' },
      responseType: 'blob',
    })
  },
  exportXlsx(locale: AppLocale): Promise<Blob> {
    return http.get('/i18n/messages/export', {
      params: { locale, format: 'xlsx' },
      responseType: 'blob',
    })
  },
}
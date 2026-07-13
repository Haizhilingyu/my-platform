import { http } from './http'

export interface MenuTranslationVO {
  id: number
  menuId: number
  menuName: string
  locale: string
  displayName: string
}

export interface MenuTranslationImportDTO {
  locale: string
  items: { menuId: number; displayName: string }[]
}

export const translationApi = {
  list: () => http.get('/sys/menu/translations') as Promise<any>,
  update: (id: number, displayName: string) => http.put(`/sys/menu/translations/${id}`, { displayName }) as Promise<any>,
  import: (data: MenuTranslationImportDTO) => http.post('/sys/menu/translations/import', data) as Promise<any>,
  export: (locale: string) => http.get(`/sys/menu/translations/export?locale=${locale}`) as Promise<any>,
}

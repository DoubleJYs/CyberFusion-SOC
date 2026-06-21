import request from './request'
import type { ApiResult } from '@/types/api'
import type { MenuItem } from '@/types/system'

export type MenuForm = Omit<MenuItem, 'id' | 'children'>

export async function fetchMenus(): Promise<MenuItem[]> {
  const response = await request.get<ApiResult<MenuItem[]>>('/system/menus')
  return response.data.data
}

export async function createMenu(data: MenuForm) {
  await request.post('/system/menus', data)
}

export async function updateMenu(id: number, data: MenuForm) {
  await request.put(`/system/menus/${id}`, data)
}

export async function deleteMenu(id: number) {
  await request.delete(`/system/menus/${id}`)
}

export async function updateMenuSort(id: number, sort: number) {
  await request.patch(`/system/menus/${id}/sort`, null, { params: { sort } })
}

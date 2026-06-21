import request from './request'
import type { ApiResult, PageQuery, PageResult } from '@/types/api'
import type { NoticeRecord } from '@/types/system'

export interface NoticeQuery extends PageQuery {
  noticeType?: string
}

export type NoticeForm = Omit<NoticeRecord, 'id' | 'createdAt' | 'updatedAt'>

export async function fetchNotices(params: NoticeQuery): Promise<PageResult<NoticeRecord>> {
  const response = await request.get<ApiResult<PageResult<NoticeRecord>>>('/system/notices', { params })
  return response.data.data
}

export async function fetchActiveNotices(params?: { limit?: number; noticeType?: string }): Promise<NoticeRecord[]> {
  const response = await request.get<ApiResult<NoticeRecord[]>>('/system/notices/active', { params })
  return response.data.data
}

export async function createNotice(data: NoticeForm) {
  await request.post('/system/notices', data)
}

export async function updateNotice(id: number, data: NoticeForm) {
  await request.put(`/system/notices/${id}`, data)
}

export async function deleteNotice(id: number) {
  await request.delete(`/system/notices/${id}`)
}

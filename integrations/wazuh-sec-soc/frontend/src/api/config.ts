import request from './request'
import type { ApiResult, PageQuery, PageResult } from '@/types/api'
import type { ConfigRecord } from '@/types/system'

export interface ConfigQuery extends PageQuery {
  groupCode?: string
}

export type ConfigForm = Omit<ConfigRecord, 'id' | 'createdAt' | 'updatedAt'>

export async function fetchConfigs(params: ConfigQuery): Promise<PageResult<ConfigRecord>> {
  const response = await request.get<ApiResult<PageResult<ConfigRecord>>>('/system/configs', { params })
  return response.data.data
}

export async function createConfig(data: ConfigForm) {
  await request.post('/system/configs', data)
}

export async function updateConfig(id: number, data: ConfigForm) {
  await request.put(`/system/configs/${id}`, data)
}

export async function deleteConfig(id: number) {
  await request.delete(`/system/configs/${id}`)
}

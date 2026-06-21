import request from './request'
import type { ApiResult, PageQuery, PageResult } from '@/types/api'
import type { DictDataRecord, DictTypeRecord } from '@/types/system'

export async function fetchDictTypes(params: PageQuery): Promise<PageResult<DictTypeRecord>> {
  const response = await request.get<ApiResult<PageResult<DictTypeRecord>>>('/system/dict-types', { params })
  return response.data.data
}

export async function saveDictType(data: Partial<DictTypeRecord>, id?: number) {
  if (id) await request.put(`/system/dict-types/${id}`, data)
  else await request.post('/system/dict-types', data)
}

export async function deleteDictType(id: number) {
  await request.delete(`/system/dict-types/${id}`)
}

export async function fetchDictData(params: PageQuery & { dictTypeId?: number }): Promise<PageResult<DictDataRecord>> {
  const response = await request.get<ApiResult<PageResult<DictDataRecord>>>('/system/dict-data', { params })
  return response.data.data
}

export async function saveDictData(data: Partial<DictDataRecord>, id?: number) {
  if (id) await request.put(`/system/dict-data/${id}`, data)
  else await request.post('/system/dict-data', data)
}

export async function deleteDictData(id: number) {
  await request.delete(`/system/dict-data/${id}`)
}

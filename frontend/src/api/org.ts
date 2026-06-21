import request from './request'
import type { ApiResult, PageQuery, PageResult } from '@/types/api'
import type { DeptRecord, PostRecord } from '@/types/system'

export interface DeptQuery {
  keyword?: string
  status?: number
}

export type DeptForm = Omit<DeptRecord, 'id' | 'createdAt' | 'children'>
export type PostForm = Omit<PostRecord, 'id' | 'createdAt'>

export async function fetchDepts(params?: DeptQuery): Promise<DeptRecord[]> {
  const response = await request.get<ApiResult<DeptRecord[]>>('/system/depts', { params })
  return response.data.data
}

export async function createDept(data: DeptForm) {
  await request.post('/system/depts', data)
}

export async function updateDept(id: number, data: DeptForm) {
  await request.put(`/system/depts/${id}`, data)
}

export async function deleteDept(id: number) {
  await request.delete(`/system/depts/${id}`)
}

export async function fetchPosts(params: PageQuery): Promise<PageResult<PostRecord>> {
  const response = await request.get<ApiResult<PageResult<PostRecord>>>('/system/posts', { params })
  return response.data.data
}

export async function createPost(data: PostForm) {
  await request.post('/system/posts', data)
}

export async function updatePost(id: number, data: PostForm) {
  await request.put(`/system/posts/${id}`, data)
}

export async function deletePost(id: number) {
  await request.delete(`/system/posts/${id}`)
}

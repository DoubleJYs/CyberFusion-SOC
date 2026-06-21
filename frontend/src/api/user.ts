import request from './request'
import type { ApiResult, PageQuery, PageResult } from '@/types/api'
import type { UserRecord } from '@/types/system'

export interface UserQuery extends PageQuery {
  deptId?: number
  postId?: number
}

export interface UserForm {
  username?: string
  nickname: string
  password?: string
  email?: string
  mobile?: string
  deptId?: number
  postId?: number
  status?: number
  roleIds?: number[]
}

export interface PasswordForm {
  oldPassword: string
  newPassword: string
}

export async function fetchUsers(params: UserQuery): Promise<PageResult<UserRecord>> {
  const response = await request.get<ApiResult<PageResult<UserRecord>>>('/system/users', { params })
  return response.data.data
}

export async function createUser(data: UserForm) {
  await request.post('/system/users', data)
}

export async function updateUser(id: number, data: UserForm) {
  await request.put(`/system/users/${id}`, data)
}

export async function changeUserStatus(id: number, status: number) {
  await request.patch(`/system/users/${id}/status`, null, { params: { status } })
}

export async function resetUserPassword(id: number, newPassword: string) {
  await request.patch(`/system/users/${id}/password`, { newPassword })
}

export async function assignUserRoles(id: number, roleIds: number[]) {
  await request.put(`/system/users/${id}/roles`, roleIds)
}

export async function changeCurrentPassword(data: PasswordForm) {
  await request.patch('/system/users/me/password', data)
}

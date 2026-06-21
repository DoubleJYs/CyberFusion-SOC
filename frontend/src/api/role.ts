import request from './request'
import type { ApiResult, PageQuery, PageResult } from '@/types/api'
import type { RoleRecord } from '@/types/system'

export interface RoleForm {
  roleCode: string
  roleName: string
  dataScope?: string
  status?: number
  menuIds?: number[]
  deptIds?: number[]
}

export async function fetchRoles(params: PageQuery): Promise<PageResult<RoleRecord>> {
  const response = await request.get<ApiResult<PageResult<RoleRecord>>>('/system/roles', { params })
  return response.data.data
}

export async function createRole(data: RoleForm) {
  await request.post('/system/roles', data)
}

export async function updateRole(id: number, data: RoleForm) {
  await request.put(`/system/roles/${id}`, data)
}

export async function changeRoleStatus(id: number, status: number) {
  await request.patch(`/system/roles/${id}/status`, null, { params: { status } })
}

export async function assignRoleMenus(id: number, menuIds: number[]) {
  await request.put(`/system/roles/${id}/menus`, menuIds)
}

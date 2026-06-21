import request from './request'
import type { ApiResult } from '@/types/api'
import type { LoginLogRecord, OperationLogRecord } from '@/types/system'

export interface Overview {
  userCount: number
  roleCount: number
  menuCount: number
  dictItemCount: number
  noticeCount: number
  todayLoginCount: number
  recentOperations: OperationLogRecord[]
}

export interface TrendItem {
  date: string
  count: number
}

export interface ModuleItem {
  name: string
  description: string
  status: string
}

export async function fetchOverview(): Promise<Overview> {
  const response = await request.get<ApiResult<Overview>>('/dashboard/overview')
  return response.data.data
}

export async function fetchRecentLogins(): Promise<LoginLogRecord[]> {
  const response = await request.get<ApiResult<LoginLogRecord[]>>('/dashboard/recent-logins')
  return response.data.data
}

export async function fetchOperationTrend(): Promise<TrendItem[]> {
  const response = await request.get<ApiResult<TrendItem[]>>('/dashboard/operation-trend')
  return response.data.data
}

export async function fetchSystemModules(): Promise<ModuleItem[]> {
  const response = await request.get<ApiResult<ModuleItem[]>>('/dashboard/system-modules')
  return response.data.data
}

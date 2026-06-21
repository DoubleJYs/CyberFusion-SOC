import request from './request'
import type { ApiResult, PageQuery, PageResult } from '@/types/api'
import type { LoginLogRecord, OperationLogRecord } from '@/types/system'

export async function fetchLoginLogs(params: PageQuery): Promise<PageResult<LoginLogRecord>> {
  const response = await request.get<ApiResult<PageResult<LoginLogRecord>>>('/system/login-logs', { params })
  return response.data.data
}

export async function fetchOperationLogs(params: PageQuery): Promise<PageResult<OperationLogRecord>> {
  const response = await request.get<ApiResult<PageResult<OperationLogRecord>>>('/system/operation-logs', { params })
  return response.data.data
}

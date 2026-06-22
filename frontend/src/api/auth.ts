import request from './request'
import type { ApiResult } from '@/types/api'
import type { LoginRequest, LoginResponse } from '@/types/user'

export async function loginApi(data: LoginRequest): Promise<LoginResponse> {
  const response = await request.post<ApiResult<LoginResponse>>('/auth/login', data, {
    headers: { 'X-Silent-Error': 'true' },
  })
  return response.data.data
}

export async function meApi(): Promise<LoginResponse> {
  const response = await request.get<ApiResult<LoginResponse>>('/auth/me')
  return response.data.data
}

export async function refreshApi(): Promise<LoginResponse> {
  const response = await request.post<ApiResult<LoginResponse>>('/auth/refresh')
  return response.data.data
}

export async function logoutApi(): Promise<void> {
  await request.post<ApiResult<void>>('/auth/logout')
}

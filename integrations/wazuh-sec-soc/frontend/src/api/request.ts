import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { clearToken, getToken, setToken } from '@/utils/storage'
import type { ApiResult } from '@/types/api'

let refreshing: Promise<string | null> | null = null
let authMessageVisible = false

const request = axios.create({
  baseURL: '/api',
  timeout: 10000,
  withCredentials: true,
})

request.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const result = response.data as ApiResult<unknown>
    if (result && typeof result.code === 'string' && result.code !== 'SUCCESS') {
      if (result.code === 'AUTH_UNAUTHORIZED' || result.code === 'AUTH_TOKEN_EXPIRED') {
        return handleUnauthorized(response.config)
      }
      if (result.code === 'AUTH_FORBIDDEN') {
        router.replace('/403')
      }
      ElMessage.error(result.message || '请求失败')
      return Promise.reject(new Error(result.message))
    }
    return response
  },
  async (error: AxiosError) => {
    if (error.response?.status === 401) {
      return handleUnauthorized(error.config as InternalAxiosRequestConfig)
    }
    if (error.response?.status === 403) {
      await router.replace('/403')
      ElMessage.error('当前账号无权限访问')
    } else {
      ElMessage.error(error.message || '网络请求失败')
    }
    return Promise.reject(error)
  },
)

async function handleUnauthorized(config?: InternalAxiosRequestConfig) {
  if (!config || config.url?.includes('/auth/refresh') || config.url?.includes('/auth/login')) {
    clearAuthAndRedirect()
    return Promise.reject(new Error('unauthorized'))
  }
  if (!refreshing) {
    refreshing = request.post<ApiResult<{ accessToken: string }>>('/auth/refresh')
      .then((response) => {
        const token = response.data.data?.accessToken
        if (token) setToken(token)
        return token || null
      })
      .finally(() => {
        refreshing = null
      })
  }
  const token = await refreshing
  if (!token) {
    clearAuthAndRedirect()
    return Promise.reject(new Error('unauthorized'))
  }
  config.headers.Authorization = `Bearer ${token}`
  return request(config)
}

function clearAuthAndRedirect() {
  clearToken()
  if (!authMessageVisible) {
    authMessageVisible = true
    ElMessage.warning('登录状态已过期')
    window.setTimeout(() => {
      authMessageVisible = false
    }, 1200)
  }
  router.replace({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
}

export default request

import axios, {
  type AxiosError,
  type AxiosRequestConfig,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios'
import type { ApiResult } from '@/api/types'
import { clearToken, getToken } from '@/utils/auth'

/** 后端约定的业务成功码 */
export const SUCCESS_CODE = 0

/** 基础路径：开发环境为 /api，由 Vite 代理到 8080 */
export const API_BASE = (import.meta.env.VITE_API_BASE_URL ?? '/api').replace(/\/+$/, '')

/**
 * 封装后的 API 异常，调用方可直接使用 message 展示错误。
 */
export class ApiError extends Error {
  readonly code: number
  readonly httpStatus?: number

  constructor(code: number, message: string, httpStatus?: number) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.httpStatus = httpStatus
  }
}

export interface HttpClient {
  get<T>(url: string, config?: AxiosRequestConfig): Promise<T>
  post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T>
  put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T>
  patch<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T>
  delete<T>(url: string, config?: AxiosRequestConfig): Promise<T>
}

function isApiResult(value: unknown): value is ApiResult<unknown> {
  return (
    typeof value === 'object' &&
    value !== null &&
    'code' in value &&
    typeof value.code === 'number' &&
    'message' in value &&
    typeof value.message === 'string'
  )
}

/**
 * 响应拦截器已将 Result.data 解包，这里仅在类型层面还原为 T。
 */
function unwrap<T>(promise: Promise<unknown>): Promise<T> {
  return promise as Promise<T>
}

function toApiError(error: AxiosError<unknown>): ApiError {
  if (error.response) {
    const payload = error.response.data
    if (isApiResult(payload)) {
      return new ApiError(payload.code, payload.message, error.response.status)
    }
    return new ApiError(
      error.response.status,
      `请求失败（HTTP ${error.response.status}）`,
      error.response.status,
    )
  }
  if (error.code === 'ECONNABORTED') {
    return new ApiError(-1, '请求超时，请稍后重试')
  }
  return new ApiError(-1, '网络异常，请确认后端服务已启动')
}

function createClient(
  baseURL: string,
  onUnauthorized?: (error: ApiError) => void,
): HttpClient {
  const instance = axios.create({
    baseURL,
    timeout: 15_000,
    headers: { 'Content-Type': 'application/json' },
  })

  instance.interceptors.request.use((config: InternalAxiosRequestConfig) => {
    const token = getToken()
    if (token) {
      config.headers.set('Authorization', `Bearer ${token}`)
    }
    if (config.data instanceof FormData) {
      config.headers.delete('Content-Type')
    }
    return config
  })

  instance.interceptors.response.use(
    (response: AxiosResponse<unknown>) => {
      const payload = response.data
      if (!isApiResult(payload)) {
        return payload as AxiosResponse
      }
      if (payload.code !== SUCCESS_CODE) {
        throw new ApiError(payload.code, payload.message, response.status)
      }
      return payload.data as AxiosResponse
    },
    (error: AxiosError<unknown>) => {
      const apiError = toApiError(error)
      if (apiError.httpStatus === 401) {
        onUnauthorized?.(apiError)
      }
      throw apiError
    },
  )

  return {
    get<T>(url: string, config?: AxiosRequestConfig) {
      return unwrap<T>(instance.get(url, config))
    },
    post<T>(url: string, data?: unknown, config?: AxiosRequestConfig) {
      return unwrap<T>(instance.post(url, data, config))
    },
    put<T>(url: string, data?: unknown, config?: AxiosRequestConfig) {
      return unwrap<T>(instance.put(url, data, config))
    },
    patch<T>(url: string, data?: unknown, config?: AxiosRequestConfig) {
      return unwrap<T>(instance.patch(url, data, config))
    },
    delete<T>(url: string, config?: AxiosRequestConfig) {
      return unwrap<T>(instance.delete(url, config))
    },
  }
}

/** 通用 /api 客户端（健康检查等非控制台接口） */
export const http = createClient(API_BASE)

/** 控制台专用客户端：自动携带 JWT，401 时清理登录态并跳转登录页 */
export const consoleHttp = createClient(`${API_BASE}/console`, () => {
  if (!getToken()) {
    return
  }
  clearToken()
  if (typeof window !== 'undefined' && !window.location.pathname.startsWith('/login')) {
    const redirect = encodeURIComponent(window.location.pathname + window.location.search)
    window.location.assign(`/login?redirect=${redirect}`)
  }
})

export default http

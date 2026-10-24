import axios, { type AxiosError } from 'axios'
import type { ApiResult } from '@/api/types'

/** 开放接口请求方式：平台约定为 POST + JSON，GET 仅用于兼容旧版示例接口。 */
export type OpenApiMethod = 'GET' | 'POST'

export interface OpenApiCallInput {
  /** 完整的开放接口路径，如 /api/v1/tools/ip-locate */
  path: string
  /** 目录中登记的请求方式 */
  method: string
  /** 明文 X-API-Key，仅保留在调试台内存中 */
  apiKey: string
  /** 用户编辑后的 JSON 请求报文 */
  payload: Record<string, unknown>
}

export interface ServiceCallResult {
  httpStatus: number
  latencyMs: number
  requestUrl: string
  response: unknown
  networkError: string | null
}

const openApi = axios.create({
  timeout: 15_000,
  headers: { 'Content-Type': 'application/json' },
})

function normalizeMethod(method: string): OpenApiMethod {
  return method.trim().toUpperCase() === 'GET' ? 'GET' : 'POST'
}

/**
 * GET 接口把 JSON 字段展开为 Query 参数，POST 接口保持原始 JSON 报文。
 * 同时用于实际请求和调试台的地址预览，避免两处逻辑不一致。
 */
export function buildRequestUrl(
  path: string,
  method: OpenApiMethod,
  payload: Record<string, unknown>,
): string {
  const url = new URL(path, window.location.origin)
  if (method === 'GET') {
    for (const [key, value] of Object.entries(payload)) {
      if (value === undefined || value === null) {
        continue
      }
      url.searchParams.set(key, typeof value === 'object' ? JSON.stringify(value) : String(value))
    }
  }
  return url.toString()
}

/**
 * 以明文 X-API-Key 调用开放接口。
 * 不使用 consoleHttp，避免误挂 JWT 或解包 Result，调试台需要展示完整响应。
 */
export async function callOpenService(input: OpenApiCallInput): Promise<ServiceCallResult> {
  const method = normalizeMethod(input.method)
  const requestUrl = buildRequestUrl(input.path, method, input.payload)

  const startedAt = performance.now()
  try {
    const response = await openApi.request<ApiResult<unknown>>({
      // GET 直接使用带 Query 的完整地址，保证实际请求与调试台展示一致。
      url: method === 'GET' ? requestUrl : input.path,
      method,
      data: method === 'POST' ? input.payload : undefined,
      headers: { 'X-API-Key': input.apiKey },
    })
    return {
      httpStatus: response.status,
      latencyMs: Math.round(performance.now() - startedAt),
      requestUrl,
      response: response.data,
      networkError: null,
    }
  } catch (error) {
    const axiosError = error as AxiosError<unknown>
    return {
      httpStatus: axiosError.response?.status ?? 0,
      latencyMs: Math.round(performance.now() - startedAt),
      requestUrl,
      response: axiosError.response?.data ?? { message: axiosError.message },
      networkError: axiosError.response ? null : axiosError.message,
    }
  }
}

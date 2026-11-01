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

/**
 * 接口路径白名单，与后端 ApiCodeGenSpec 的 @Pattern 保持一致。
 *
 * 调试台会把用户刚粘贴的明文 X-API-Key 发往这里解析出的地址，因此不能信任
 * 接口目录中的 path：协议相对地址（//evil.com/x）、绝对 URL 或反斜杠变体
 * 都会让 Key 被带到外部站点。接口目录即使被直接改库污染，也要在这里拦住。
 */
const SAFE_ENDPOINT_PATH = /^\/api\/v1\/[A-Za-z0-9_/-]+$/

export function isSafeEndpointPath(path: string): boolean {
  return SAFE_ENDPOINT_PATH.test(path)
}

function normalizeMethod(method: string): OpenApiMethod {
  return method.trim().toUpperCase() === 'GET' ? 'GET' : 'POST'
}

/**
 * GET 接口把 JSON 字段展开为 Query 参数，POST 接口保持原始 JSON 报文。
 * 同时用于实际请求和调试台的地址预览，避免两处逻辑不一致。
 * 路径不合法或解析后与当前站点不同源时返回空串，调用方不得据此发送请求。
 */
export function buildRequestUrl(
  path: string,
  method: OpenApiMethod,
  payload: Record<string, unknown>,
): string {
  if (!isSafeEndpointPath(path)) {
    return ''
  }
  const url = new URL(path, window.location.origin)
  // 兜底：即使白名单被绕过，也要求最终地址与当前站点同源
  if (url.origin !== window.location.origin) {
    return ''
  }
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
  if (!isSafeEndpointPath(input.path)) {
    throw new Error('接口路径不合法，已阻止发送请求')
  }
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

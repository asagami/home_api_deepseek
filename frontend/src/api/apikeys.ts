import type { PageResult } from '@/api/types'
import { consoleHttp } from '@/utils/request'

export interface ApiKeyCreatePayload {
  keyName: string
  rateLimitQps?: number
  /** LocalDateTime（ISO 字符串），null 表示永不过期 */
  expiredTime?: string | null
}

export interface ApiKeyCreateResult {
  /** 雪花 ID 超出 JS 安全整数范围，后端以字符串下发，避免精度丢失 */
  id: string
  keyName: string
  keyPrefix: string
  /** 完整明文 Key，仅创建响应返回一次 */
  apiKey: string
  rateLimitQps: number
  totalQuota: number
  usedQuota: number
  expiredTime: string | null
  status: number
  createTime: string
}

export interface ApiKeyItem {
  /** 雪花 ID 超出 JS 安全整数范围，后端以字符串下发，避免精度丢失 */
  id: string
  keyName: string
  keyPrefix: string
  rateLimitQps: number
  totalQuota: number
  usedQuota: number
  expiredTime: string | null
  lastUsedTime: string | null
  status: number
  createTime: string
}

export interface ApiKeyListQuery {
  page: number
  size: number
}

export type ApiKeyStatus = 0 | 1

export function createApiKey(payload: ApiKeyCreatePayload): Promise<ApiKeyCreateResult> {
  return consoleHttp.post<ApiKeyCreateResult>('/apikeys', payload)
}

export function listApiKeys(query: ApiKeyListQuery): Promise<PageResult<ApiKeyItem>> {
  return consoleHttp.get<PageResult<ApiKeyItem>>('/apikeys', { params: query })
}

export function updateApiKeyStatus(id: string, status: ApiKeyStatus): Promise<void> {
  return consoleHttp.patch<void>(`/apikeys/${id}/status`, { status })
}

export function deleteApiKey(id: string): Promise<void> {
  return consoleHttp.delete<void>(`/apikeys/${id}`)
}

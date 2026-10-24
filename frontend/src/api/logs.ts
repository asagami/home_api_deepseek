import type { PageResult } from '@/api/types'
import { consoleHttp } from '@/utils/request'

export interface LogItem {
  /** 后端以字符串返回雪花 ID，避免 JS 精度丢失 */
  id: string
  userId: string
  apiKeyId: string
  endpointPath: string
  httpStatus: number
  latencyMs: number
  clientIp: string | null
  createTime: string
}

export interface LogListQuery {
  page: number
  size: number
  /** ISO LocalDateTime，如 2026-10-16T00:00:00 */
  startTime?: string
  /** ISO LocalDateTime，包含该时刻 */
  endTime?: string
  httpStatus?: number
}

export interface LogDailyStat {
  date: string
  totalCalls: number
  successCalls: number
  clientErrorCalls: number
  serverErrorCalls: number
  avgLatencyMs: number
}

export interface LogStats {
  startDate: string
  endDate: string
  totalCalls: number
  successCalls: number
  clientErrorCalls: number
  serverErrorCalls: number
  avgLatencyMs: number
  /** 百分比，0-100 */
  successRate: number
  daily: LogDailyStat[]
}

export function listLogs(query: LogListQuery): Promise<PageResult<LogItem>> {
  return consoleHttp.get<PageResult<LogItem>>('/logs', { params: query })
}

/** 默认统计含今天在内的最近 7 天 */
export function getLogStats(): Promise<LogStats> {
  return consoleHttp.get<LogStats>('/logs/stats')
}

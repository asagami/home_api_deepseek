import type { PageResult } from '@/api/types'
import { consoleHttp } from '@/utils/request'

/** 单日调用趋势 */
export interface DailyTrendItem {
  date: string
  totalCalls: number
  successCalls: number
  rateLimitCount: number
  error5xxCount: number
  costPoints: number
  avgLatencyMs: number
  /** 百分比，0-100 */
  successRate: number
}

/** 接口调用排行项 */
export interface EndpointRankItem {
  endpointPath: string
  endpointName: string
  callCount: number
  successCalls: number
  costPoints: number
  rateLimitCount: number
  error5xxCount: number
  avgLatencyMs: number
  /** 百分比，0-100 */
  successRate: number
}

/** 点数消耗排行项 */
export interface UserConsumeRankItem {
  /** 雪花 ID 以字符串下发 */
  userId: string
  username: string | null
  costPoints: number
  callCount: number
}

/** 管理员全站调用总览 */
export interface AdminLogOverview {
  startTime: string
  endTime: string
  totalCalls: number
  totalCostPoints: number
  /** 百分比，0-100 */
  successRate: number
  rateLimitCount: number
  error5xxCount: number
  avgLatencyMs: number
  dailyTrend: DailyTrendItem[]
  endpointRank: EndpointRankItem[]
  topConsumeUsers: UserConsumeRankItem[]
}

/** 全站日志明细行 */
export interface AdminLogItem {
  /** 雪花 ID 以字符串下发 */
  id: string
  userId: string
  username: string | null
  apiKeyId: string
  keyPrefix: string | null
  endpointPath: string
  endpointName: string | null
  httpStatus: number
  costPoints: number
  latencyMs: number
  clientIp: string | null
  createTime: string
}

export interface AdminLogOverviewQuery {
  /** ISO LocalDateTime；为空时后端默认最近 7 天 */
  startTime?: string
  endTime?: string
}

export interface AdminLogPageFilters {
  /** 后端强制要求时间范围以命中 create_time 索引 */
  startTime: string
  endTime: string
  userId?: string
  username?: string
  endpointPath?: string
  httpStatus?: number
  minLatencyMs?: number
}

export interface AdminLogPageQuery extends AdminLogPageFilters {
  pageNum: number
  pageSize: number
}

export function getAdminLogOverview(query: AdminLogOverviewQuery): Promise<AdminLogOverview> {
  return consoleHttp.post<AdminLogOverview>('/admin/logs/overview', query)
}

export function pageAdminLogs(query: AdminLogPageQuery): Promise<PageResult<AdminLogItem>> {
  return consoleHttp.post<PageResult<AdminLogItem>>('/admin/logs/page', query)
}

import type { PageResult } from '@/api/types'
import { consoleHttp } from '@/utils/request'

/** 0-待审批，1-已通过，2-已驳回，3-已收回 */
export type PermissionStatus = 0 | 1 | 2 | 3

export type PermissionAuditAction = 'APPROVE' | 'REJECT' | 'REVOKE'

export interface EndpointMarketItem {
  /** 雪花 ID 以字符串下发，避免 JS 精度丢失 */
  id: string
  name: string
  path: string
  method: string
  description: string | null
  baseCost: number
  /** 当前用户实际适用的折后点数；未通过审批时等于基础点数 */
  discountedCost: number
  discountRate: number
  /** null 表示当前用户尚未申请 */
  applyStatus: PermissionStatus | null
  auditRemark: string | null
  applyTime: string | null
  /** 1-上线，0-维护中 */
  endpointStatus: number
  requestSchema: string | null
  responseSchema: string | null
}

export interface PermissionApplyPayload {
  endpointId: string
  applyReason: string
}

export interface UserPermissionItem {
  id: string
  endpointId: string
  status: PermissionStatus
  applyReason: string
  customDiscount: number
  auditRemark: string | null
  createTime: string
  auditTime: string | null
}

export interface AdminPermissionItem {
  id: string
  userId: string
  username: string | null
  endpointId: string
  endpointName: string | null
  endpointPath: string | null
  endpointMethod: string | null
  baseCost: number
  applyReason: string
  status: PermissionStatus
  customDiscount: number
  /** 当前生效的单次扣费点数；未通过审批时等于基础点数 */
  actualCost: number
  auditRemark: string | null
  auditorId: string | null
  auditTime: string | null
  createTime: string
}

export interface AdminPermissionListQuery {
  page: number
  size: number
  status?: number
  keyword?: string
}

export interface PermissionAuditPayload {
  action: PermissionAuditAction
  /** 仅 APPROVE 时生效，0.00-1.00 */
  customDiscount?: number
  auditRemark?: string
}

/** 管理员视角的 API 目录条目（API 发布管理） */
export interface AdminEndpointItem {
  id: string
  name: string
  path: string
  method: string
  engineType: string
  baseCost: number
  description: string | null
  /** 1-已上线，0-已下线（新增接口默认下线） */
  endpointStatus: number
  createTime: string | null
  updateTime: string | null
}

export interface AdminEndpointListQuery {
  status?: number
  keyword?: string
}

/** 当前用户视角的接口市场：所有接口 + 自己的申请状态 */
export function getMarket(): Promise<EndpointMarketItem[]> {
  return consoleHttp.get<EndpointMarketItem[]>('/permissions/market')
}

/** 提交或重新提交接口权限申请 */
export function applyPermission(payload: PermissionApplyPayload): Promise<UserPermissionItem> {
  return consoleHttp.post<UserPermissionItem>('/permissions/apply', payload)
}

/** 管理员分页查询权限申请工单 */
export function listAdminPermissions(
  query: AdminPermissionListQuery,
): Promise<PageResult<AdminPermissionItem>> {
  return consoleHttp.get<PageResult<AdminPermissionItem>>('/admin/permissions/list', {
    params: query,
  })
}

/** 管理员审批：通过、驳回或收回权限 */
export function auditPermission(
  id: string,
  payload: PermissionAuditPayload,
): Promise<UserPermissionItem> {
  return consoleHttp.put<UserPermissionItem>(`/admin/permissions/${id}/audit`, payload)
}

/** 管理员修改接口基础点数 */
export function updateEndpointCost(endpointId: string, costPerCall: number): Promise<void> {
  return consoleHttp.put<void>(`/admin/permissions/endpoints/${endpointId}/cost`, { costPerCall })
}

/** 管理员查询 API 目录（含上下线状态） */
export function listAdminEndpoints(query: AdminEndpointListQuery = {}): Promise<AdminEndpointItem[]> {
  return consoleHttp.get<AdminEndpointItem[]>('/admin/endpoints/list', { params: query })
}

/** 管理员变更 API 上下线状态：1-上线，0-下线 */
export function updateEndpointStatus(
  endpointId: string,
  status: 0 | 1,
): Promise<AdminEndpointItem> {
  return consoleHttp.put<AdminEndpointItem>(`/admin/endpoints/${endpointId}/status`, { status })
}

import type { PageResult } from '@/api/types'
import { consoleHttp } from '@/utils/request'

export interface AdminUserItem {
  /** 雪花 ID 超出 JS 安全整数范围，后端以字符串下发，避免精度丢失 */
  id: string
  username: string
  email: string | null
  role: 'USER' | 'ADMIN' | string
  balanceQuota: number
  status: number
  createTime: string
  updateTime: string
}

export interface AdminUserListQuery {
  page: number
  size: number
  keyword?: string
  status?: number
}

export type UserStatus = 0 | 1

export type AdminRechargeSource = 1 | 2

export interface AdminRechargeQuota {
  /** 统计月份，格式 yyyy-MM */
  month: string
  /** 每管理员每账号每自然月上限 */
  limit: number
  /** 当前管理员本月已为该账号充值 */
  used: number
  /** 当前管理员本月剩余可充值 */
  remaining: number
}

export interface AdminRechargeOrderItem {
  orderNo: string
  source: AdminRechargeSource
  sourceDesc: string
  packageId: string
  amountFen: number
  totalPoints: number
  status: 0 | 1 | 2
  statusDesc: string
  payChannelName: string
  operatorId: string | null
  operatorName: string | null
  remark: string | null
  payTime: string | null
  createTime: string
}

export interface AdminRechargeOrderQuery {
  pageNum: number
  pageSize: number
  source?: AdminRechargeSource
  status?: 0 | 1 | 2
}

export function listAdminUsers(query: AdminUserListQuery): Promise<PageResult<AdminUserItem>> {
  return consoleHttp.get<PageResult<AdminUserItem>>('/admin/users', { params: query })
}

export function updateUserStatus(id: string, status: UserStatus): Promise<void> {
  return consoleHttp.patch<void>(`/admin/users/${id}/status`, { status })
}

/** 为用户追加额度，返回更新后的用户信息 */
export function rechargeUserQuota(
  id: string,
  amount: number,
  remark?: string,
): Promise<AdminUserItem> {
  const payload: { amount: number; remark?: string } = { amount }
  const trimmed = remark?.trim()
  if (trimmed) {
    payload.remark = trimmed
  }
  return consoleHttp.post<AdminUserItem>(`/admin/users/${id}/quota`, payload)
}

/** 查询当前管理员本月对指定账号的充值额度使用情况 */
export function getAdminRechargeQuota(id: string): Promise<AdminRechargeQuota> {
  return consoleHttp.get<AdminRechargeQuota>(`/admin/users/${id}/recharge-quota`)
}

/** 管理员视角：分页查询指定用户的用户支付与管理员发放记录 */
export function listUserRechargeOrders(
  id: string,
  query: AdminRechargeOrderQuery,
): Promise<PageResult<AdminRechargeOrderItem>> {
  return consoleHttp.get<PageResult<AdminRechargeOrderItem>>(
    `/admin/users/${id}/recharge-orders`,
    {
      // 该接口沿用用户管理列表的 query 风格 page/size；
      // usePagination 默认传 pageNum/pageSize，这里必须显式映射，否则分页会被后端忽略。
      params: {
        page: query.pageNum,
        size: query.pageSize,
        source: query.source,
        status: query.status,
      },
    },
  )
}

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

export function listAdminUsers(query: AdminUserListQuery): Promise<PageResult<AdminUserItem>> {
  return consoleHttp.get<PageResult<AdminUserItem>>('/admin/users', { params: query })
}

export function updateUserStatus(id: string, status: UserStatus): Promise<void> {
  return consoleHttp.patch<void>(`/admin/users/${id}/status`, { status })
}

/** 为用户追加额度，返回更新后的用户信息 */
export function rechargeUserQuota(id: string, amount: number): Promise<AdminUserItem> {
  return consoleHttp.post<AdminUserItem>(`/admin/users/${id}/quota`, { amount })
}

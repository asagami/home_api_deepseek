import type { PageResult } from '@/api/types'
import { consoleHttp } from '@/utils/request'

export type PayChannel = 'ALIPAY' | 'WECHAT'

export type RechargeOrderStatus = 0 | 1 | 2

/** 1-用户充值，2-管理员发放 */
export type RechargeOrderSource = 1 | 2

export interface RechargePackage {
  packageId: string
  name: string
  /** 实付金额，单位：分 */
  amountFen: number
  basePoints: number
  bonusPoints: number
  totalPoints: number
  hot: boolean
  bestValue: boolean
}

export interface RechargeOrderCreatePayload {
  packageId: string
  payChannel: PayChannel
}

export interface RechargeOrderCreateResult {
  orderNo: string
  packageId: string
  amountFen: number
  basePoints: number
  bonusPoints: number
  totalPoints: number
  payChannel: PayChannel
  payChannelName: string
  status: RechargeOrderStatus
  statusDesc: string
  qrCodeUrl: string
  expireTime: string
  createTime: string
}

export interface RechargeOrderStatusResult {
  orderNo: string
  packageId: string
  totalPoints: number
  status: RechargeOrderStatus
  statusDesc: string
  balanceQuota: number
  tradeNo: string | null
  payTime: string | null
  expireTime: string
}

export interface RechargeOrderItem {
  orderNo: string
  packageId: string
  amountFen: number
  totalPoints: number
  /** 管理员发放的流水渠道为 ADMIN */
  payChannel: PayChannel | 'ADMIN'
  payChannelName: string
  source: RechargeOrderSource
  sourceDesc: string
  status: RechargeOrderStatus
  statusDesc: string
  tradeNo: string | null
  payTime: string | null
  expireTime: string
  createTime: string
}

export interface RechargeOrderQuery {
  pageNum: number
  pageSize: number
  /** 0-待支付，1-已支付，2-已关闭；不传表示全部 */
  status?: RechargeOrderStatus
}

export function listRechargePackages(): Promise<RechargePackage[]> {
  return consoleHttp.get<RechargePackage[]>('/recharge/packages')
}

export function createRechargeOrder(
  payload: RechargeOrderCreatePayload,
): Promise<RechargeOrderCreateResult> {
  return consoleHttp.post<RechargeOrderCreateResult>('/recharge/create-order', payload)
}

export function getRechargeOrderStatus(orderNo: string): Promise<RechargeOrderStatusResult> {
  return consoleHttp.get<RechargeOrderStatusResult>(
    `/recharge/status/${encodeURIComponent(orderNo)}`,
  )
}

export function listRechargeOrders(
  query: RechargeOrderQuery,
): Promise<PageResult<RechargeOrderItem>> {
  return consoleHttp.post<PageResult<RechargeOrderItem>>('/recharge/orders', query)
}

/** 开发环境沙箱支付：以 MOCK_ 流水号完成支付并即时入账 */
export function mockPayRechargeOrder(orderNo: string): Promise<RechargeOrderStatusResult> {
  return consoleHttp.post<RechargeOrderStatusResult>(
    `/recharge/mock-pay/${encodeURIComponent(orderNo)}`,
  )
}

import { consoleHttp } from '@/utils/request'

export interface RegisterPayload {
  username: string
  email: string
  code: string
  password: string
}

export interface LoginPayload {
  username: string
  password: string
}

export interface UserProfile {
  /** 雪花 ID 超出 JS 安全整数范围，后端以字符串下发，避免精度丢失 */
  id: string
  username: string
  email: string | null
  role: string
  balanceQuota: number
  status: number
  createTime: string
}

export interface LoginResult {
  accessToken: string
  tokenType: string
  expiresIn: number
}

export interface ResetTokenVerifyResult {
  /** 一次性 Reset Ticket，提交新密码时使用，仅保存在页面内存中 */
  ticket: string
  expiresInSeconds: number
  maskedEmail: string
}

export interface ChangePasswordPayload {
  oldPassword: string
  newPassword: string
}

export function sendEmailCode(email: string, captchaToken?: string): Promise<void> {
  return consoleHttp.post<void>('/auth/send-email-code', { email, captchaToken })
}

export function register(payload: RegisterPayload): Promise<UserProfile> {
  return consoleHttp.post<UserProfile>('/auth/register', payload)
}

export function login(payload: LoginPayload): Promise<LoginResult> {
  return consoleHttp.post<LoginResult>('/auth/login', payload)
}

export function getMe(): Promise<UserProfile> {
  return consoleHttp.get<UserProfile>('/auth/me')
}

/** 申请找回密码；无论账号是否存在，接口都返回成功，避免账号枚举 */
export function requestPasswordReset(account: string, captchaToken?: string): Promise<void> {
  return consoleHttp.post<void>('/auth/forgot-password', { account, captchaToken })
}

/** 校验邮件中的重置令牌，换取短期一次性票据 */
export function verifyResetToken(token: string): Promise<ResetTokenVerifyResult> {
  return consoleHttp.post<ResetTokenVerifyResult>('/auth/reset-password/verify', { token })
}

/** 使用一次性票据提交新密码 */
export function resetPassword(ticket: string, newPassword: string): Promise<void> {
  return consoleHttp.post<void>('/auth/reset-password', { ticket, newPassword })
}

/**
 * 修改密码：成功后服务端返回当前设备的新令牌，其他设备的历史登录全部失效。
 */
export function changePassword(payload: ChangePasswordPayload): Promise<LoginResult> {
  return consoleHttp.post<LoginResult>('/auth/change-password', payload)
}

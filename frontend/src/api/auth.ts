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

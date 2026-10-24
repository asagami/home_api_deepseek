import http from '@/utils/request'

export interface HealthInfo {
  status: string
  service: string
  version: string
  timestamp: number
}

/** 获取后端健康状态 */
export function fetchHealth(): Promise<HealthInfo> {
  return http.get<HealthInfo>('/health')
}

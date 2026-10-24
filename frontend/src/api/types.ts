/** 后端统一响应结构，对应 Result<T> */
export interface ApiResult<T> {
  code: number
  message: string
  data: T
  timestamp: number
}

/** 后端统一分页结构，对应 PageResult<T> */
export interface PageResult<T> {
  total: number
  pageNum: number
  pageSize: number
  hasMore: boolean
  nextCursor: string | null
  records: T[]
}

export interface ApiErrorPayload {
  code: number
  message: string
  data: null
  timestamp: number
}

import { onMounted, ref, shallowRef } from 'vue'

/** 分页请求的公共参数 */
export interface PageQuery {
  pageNum: number
  pageSize: number
}

/** 与后端 PageResult<T> 对齐的最小分页响应契约 */
export interface PageResultLike<T> {
  total: number
  pageNum: number
  pageSize: number
  records: T[]
}

export interface UsePaginationOptions {
  /** 初始每页条数，默认 20 */
  pageSize?: number
  /** 可选的每页条数，默认 [10, 20, 50, 100] */
  pageSizes?: number[]
  /** 是否在 onMounted 时自动加载，默认 false */
  immediate?: boolean
  /** 统一错误处理，避免调用方对每个加载入口重复 try/catch */
  onError?: (error: unknown) => void
}

/**
 * 通用分页联动：维护页码、每页条数、总数与加载态，并把筛选条件交给 fetcher。
 *
 * fetcher 收到的是「筛选条件 + pageNum/pageSize」的合并参数；buildQuery 每次
 * 加载前重新求值，因此调用方的筛选表单可以是 reactive/ref，无需手动同步。
 */
export function usePagination<T, Q extends object>(
  fetcher: (query: Q & PageQuery) => Promise<PageResultLike<T>>,
  buildQuery: () => Q,
  options: UsePaginationOptions = {},
) {
  const records = shallowRef<T[]>([])
  const total = ref(0)
  const pageNum = ref(1)
  const pageSize = ref(options.pageSize ?? 20)
  const pageSizes = options.pageSizes ?? [10, 20, 50, 100]
  const loading = ref(false)

  async function load(): Promise<void> {
    loading.value = true
    try {
      const result = await fetcher({
        ...buildQuery(),
        pageNum: pageNum.value,
        pageSize: pageSize.value,
      })
      records.value = result.records
      total.value = result.total
      pageNum.value = result.pageNum
      pageSize.value = result.pageSize
    } catch (error) {
      options.onError?.(error)
    } finally {
      loading.value = false
    }
  }

  /** 条件变化后从第一页重新查询 */
  function search(): void {
    pageNum.value = 1
    void load()
  }

  function handleCurrentChange(value: number): void {
    pageNum.value = value
    void load()
  }

  function handleSizeChange(value: number): void {
    pageSize.value = value
    pageNum.value = 1
    void load()
  }

  onMounted(() => {
    if (options.immediate) {
      void load()
    }
  })

  return {
    records,
    total,
    pageNum,
    pageSize,
    pageSizes,
    loading,
    load,
    search,
    handleCurrentChange,
    handleSizeChange,
  }
}

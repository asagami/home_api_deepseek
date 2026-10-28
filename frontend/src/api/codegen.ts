import { consoleHttp } from '@/utils/request'

export interface FieldSpec {
  name: string
  type: string
  description?: string
  example?: string
  required?: boolean
  maxLength?: number
}

export interface ApiCodeGenSpec {
  domain: string
  apiName: string
  description?: string
  endpointPath: string
  costPerCall: number
  engineType: 'INTERNAL_SQL' | 'EXTERNAL_SQL' | 'ES' | 'HTTP_PROXY'
  targetDataSourceKey?: string
  paged?: boolean
  customSql?: string
  esIndexName?: string
  upstreamBaseUrl?: string
  upstreamHttpMethod?: string
  upstreamHeaders?: string
  requestFields: FieldSpec[]
  responseFields: FieldSpec[]
}

export interface CodeGenPreviewVO {
  reqClassName: string
  reqCode: string
  voClassName: string
  voCode: string
  docClassName?: string
  docCode?: string
  handlerClassName: string
  handlerCode: string
  metaSql: string
}

export interface DataSourceOptionVO {
  key: string
  name: string
  dbType: string
  jdbcUrl: string
}

export interface ApiEndpointSummaryVO {
  path: string
  name: string
  engineType: string
  costPerCall: number
  hasConfig: boolean
}

/** 实时预览生成的代码 (内存渲染) */
export function previewCodeGen(spec: ApiCodeGenSpec): Promise<CodeGenPreviewVO> {
  return consoleHttp.post<CodeGenPreviewVO>('/admin/codegen/preview', spec)
}

/** 将生成的类安全写入工作区 (带防覆盖保护)，返回落盘的绝对路径 */
export function writeCodeGenToDisk(spec: ApiCodeGenSpec): Promise<string> {
  return consoleHttp.post<string>('/admin/codegen/write-disk', spec)
}

/** 更新已有接口的代码 (仅更新 Req、VO 与 SQL，保留已有核心代码) */
export function updateCodeGenWorkspace(spec: ApiCodeGenSpec): Promise<string> {
  return consoleHttp.post<string>('/admin/codegen/update', spec)
}

/** 获取系统中已登记的开放接口列表 (供下拉选择修改) */
export function listConfiguredEndpoints(): Promise<ApiEndpointSummaryVO[]> {
  return consoleHttp.get<ApiEndpointSummaryVO[]>('/admin/codegen/endpoints')
}

/** 加载指定接口历史代码生成规格 */
export function getEndpointDetail(path: string): Promise<ApiCodeGenSpec> {
  return consoleHttp.get<ApiCodeGenSpec>('/admin/codegen/endpoint-detail', { params: { path } })
}

/** 获取已就绪的外部数据源列表 */
export function listCodeGenDataSources(): Promise<DataSourceOptionVO[]> {
  return consoleHttp.get<DataSourceOptionVO[]>('/admin/codegen/datasources')
}

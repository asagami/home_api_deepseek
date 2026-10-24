import type { EndpointMarketItem } from '@/api/permission'

/**
 * 内置接口的请求示例。
 *
 * 接口目录中的 request_schema 为空时使用这里的兜底示例，
 * 保证调试台在目录尚未维护示例的情况下依然可直接发送请求。
 */
const FALLBACK_SAMPLES: Record<string, Record<string, unknown>> = {
  '/api/v1/services/echo': { message: 'Hello, API!' },
  '/api/v1/services/text-stats': { text: 'Hello world, hello API! hello' },
  '/api/v1/data/my-logs': { pageNum: 1, pageSize: 20, httpStatus: 200 },
  '/api/v1/search/news': { keyword: '人工智能', pageNum: 1, pageSize: 20 },
  '/api/v1/tools/ip-locate': { ip: '8.8.8.8' },
}

/** 从 JSON Schema 的 properties 生成一份可发送的示例对象。 */
function sampleFromProperties(properties: Record<string, unknown>): Record<string, unknown> {
  const sample: Record<string, unknown> = {}
  for (const [key, property] of Object.entries(properties)) {
    sample[key] = sampleFromSchema(property)
  }
  return sample
}

function sampleFromSchema(schema: unknown): unknown {
  if (!isRecord(schema)) {
    return null
  }
  if (schema.example !== undefined) {
    return schema.example
  }
  if (schema.default !== undefined) {
    return schema.default
  }
  if (Array.isArray(schema.enum) && schema.enum.length > 0) {
    return schema.enum[0]
  }

  switch (schema.type) {
    case 'object':
      return isRecord(schema.properties) ? sampleFromProperties(schema.properties) : {}
    case 'array':
      return isRecord(schema.items) ? [sampleFromSchema(schema.items)] : []
    case 'integer':
    case 'number':
      return 1
    case 'boolean':
      return true
    case 'string':
      return 'string'
    default:
      return null
  }
}

/** request_schema 可能是示例对象，也可能是 JSON Schema，两种形式都兼容。 */
function sampleFromRequestSchema(requestSchema: string | null): Record<string, unknown> | null {
  if (!requestSchema || !requestSchema.trim()) {
    return null
  }
  try {
    const parsed: unknown = JSON.parse(requestSchema)
    if (!isRecord(parsed)) {
      return null
    }
    if (parsed.type === 'object' && isRecord(parsed.properties)) {
      return sampleFromProperties(parsed.properties)
    }
    return parsed
  } catch {
    return null
  }
}

/**
 * 为所选接口生成请求报文示例。
 * 优先使用接口目录维护的 request_schema，缺失时回退到内置示例。
 */
export function buildRequestSample(
  endpoint: Pick<EndpointMarketItem, 'path' | 'requestSchema'>,
): Record<string, unknown> {
  return sampleFromRequestSchema(endpoint.requestSchema) ?? FALLBACK_SAMPLES[endpoint.path] ?? {}
}

/** 以两空格缩进输出，便于在文本框中直接编辑。 */
export function formatSample(sample: unknown): string {
  return JSON.stringify(sample, null, 2)
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

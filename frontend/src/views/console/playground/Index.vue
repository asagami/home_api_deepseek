<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { CopyDocument, Key, Promotion, Refresh } from '@element-plus/icons-vue'
import { buildRequestUrl, callOpenService, type ServiceCallResult } from '@/api/open'
import { getMarket, type EndpointMarketItem } from '@/api/permission'
import { copyText } from '@/utils/clipboard'
import { toErrorMessage } from '@/utils/error'
import { buildRequestSample, formatSample } from '@/utils/requestSample'
import { useAuthStore } from '@/stores/auth'

defineOptions({ name: 'PlaygroundView' })

type CodeTab = 'curl' | 'python'

const auth = useAuthStore()
const route = useRoute()

const endpoints = ref<EndpointMarketItem[]>([])
const loadingEndpoints = ref(false)
const selectedEndpointId = ref('')
const apiKey = ref('')
const requestBody = ref('')
const sending = ref(false)
const result = ref<ServiceCallResult | null>(null)
const codeTab = ref<CodeTab>('curl')

/** 仅审批通过且在线可用的接口允许在调试台调用。 */
const selectableEndpoints = computed(() =>
  endpoints.value.filter((item) => item.applyStatus === 1 && item.endpointStatus === 1),
)

const hasSelectableEndpoints = computed(() => selectableEndpoints.value.length > 0)

const hasApprovedEndpoints = computed(() =>
  endpoints.value.some((item) => item.applyStatus === 1),
)

const selectedEndpoint = computed(
  () => selectableEndpoints.value.find((item) => item.id === selectedEndpointId.value) ?? null,
)

const requestMethod = computed(() =>
  selectedEndpoint.value?.method.trim().toUpperCase() === 'GET' ? 'GET' : 'POST',
)

const payloadPreview = computed<Record<string, unknown> | null>(() => parsePayload(requestBody.value))

const requestUrl = computed(() => {
  const endpoint = selectedEndpoint.value
  if (!endpoint) {
    return ''
  }
  return buildRequestUrl(endpoint.path, requestMethod.value, payloadPreview.value ?? {})
})

const effectiveKey = computed(() => apiKey.value.trim() || 'YOUR_API_KEY')

const prettyResponse = computed(() => {
  if (!result.value) {
    return ''
  }
  try {
    return JSON.stringify(result.value.response, null, 2)
  } catch {
    return String(result.value.response)
  }
})

const isSuccess = computed(
  () =>
    result.value !== null &&
    result.value.httpStatus >= 200 &&
    result.value.httpStatus < 300 &&
    !result.value.networkError,
)

const curlCode = computed(() => {
  const endpoint = selectedEndpoint.value
  if (!endpoint) {
    return ''
  }
  const args = [
    `-X ${requestMethod.value}`,
    shellQuote(requestUrl.value),
    `-H ${shellQuote(`X-API-Key: ${effectiveKey.value}`)}`,
  ]
  if (requestMethod.value !== 'GET') {
    args.push(`-H ${shellQuote('Content-Type: application/json')}`)
    args.push(`-d ${shellQuote(requestBodySummary())}`)
  }
  return `curl ${args.join(' \\\n  ')}`
})

const pythonCode = computed(() => {
  const endpoint = selectedEndpoint.value
  if (!endpoint) {
    return ''
  }
  const lines = [
    'import requests',
    '',
    `url = ${jsonLiteral(requestUrl.value)}`,
    'headers = {',
    `    "X-API-Key": ${jsonLiteral(effectiveKey.value)},`,
    '}',
  ]
  if (requestMethod.value === 'GET') {
    lines.push('', 'response = requests.get(url, headers=headers)')
  } else {
    lines.push(
      '',
      `payload = ${jsonLiteral(payloadPreview.value ?? {})}`,
      '',
      'response = requests.post(url, headers=headers, json=payload)',
    )
  }
  lines.push('', 'print(response.status_code)', 'print(response.json())')
  return lines.join('\n')
})

const codeSnippet = computed(() => (codeTab.value === 'curl' ? curlCode.value : pythonCode.value))

watch(selectedEndpointId, () => {
  result.value = null
  resetSample(false)
})

function parsePayload(raw: string): Record<string, unknown> | null {
  try {
    const parsed: unknown = JSON.parse(raw.trim() || '{}')
    if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
      return null
    }
    return parsed as Record<string, unknown>
  } catch {
    return null
  }
}

function requestBodySummary(): string {
  return JSON.stringify(payloadPreview.value ?? {})
}

function jsonLiteral(value: unknown): string {
  return JSON.stringify(value) ?? 'null'
}

function shellQuote(value: string): string {
  return `'${value.replace(/'/g, `'\\''`)}'`
}

function applyDefaultSelection(preferRouteEndpoint: boolean): void {
  const routeEndpointId =
    preferRouteEndpoint && typeof route.query.endpointId === 'string' ? route.query.endpointId : ''

  if (routeEndpointId) {
    const target = selectableEndpoints.value.find((item) => item.id === routeEndpointId)
    if (target) {
      selectedEndpointId.value = target.id
      ElMessage.success(`已选择「${target.name}」，请求报文已按示例生成`)
      return
    }
    const inMarket = endpoints.value.find((item) => item.id === routeEndpointId)
    if (inMarket) {
      ElMessage.info(`「${inMarket.name}」尚未获得调用权限，请先在 API 市场申请并通过审批`)
    }
  }

  if (!selectableEndpoints.value.some((item) => item.id === selectedEndpointId.value)) {
    selectedEndpointId.value = selectableEndpoints.value[0]?.id ?? ''
  }
}

/**
 * 加载接口市场并只保留当前用户已通过审批的接口。
 * 从市场跳转时携带 endpointId，用于自动选中对应接口。
 */
async function loadEndpoints(preferRouteEndpoint: boolean): Promise<void> {
  loadingEndpoints.value = true
  try {
    endpoints.value = await getMarket()
    applyDefaultSelection(preferRouteEndpoint)
  } catch (error) {
    endpoints.value = []
    selectedEndpointId.value = ''
    ElMessage.error(`加载可调试接口失败：${toErrorMessage(error)}`)
  } finally {
    loadingEndpoints.value = false
  }
}

onMounted(() => {
  void loadEndpoints(true)
})

function resetSample(notify = true): void {
  const endpoint = selectedEndpoint.value
  if (!endpoint) {
    requestBody.value = ''
    return
  }
  requestBody.value = formatSample(buildRequestSample(endpoint))
  if (notify) {
    ElMessage.success('已恢复请求示例')
  }
}

function formatRequestBody(): void {
  const payload = payloadPreview.value
  if (!payload) {
    ElMessage.warning('请求报文必须是合法的 JSON 对象')
    return
  }
  requestBody.value = JSON.stringify(payload, null, 2)
}

async function sendRequest(): Promise<void> {
  const endpoint = selectedEndpoint.value
  if (!endpoint) {
    ElMessage.warning('请先选择已通过审批的接口')
    return
  }
  if (!apiKey.value.trim()) {
    ElMessage.warning('请先填写明文 X-API-Key')
    return
  }
  const payload = payloadPreview.value
  if (!payload) {
    ElMessage.error('请求报文必须是合法的 JSON 对象')
    return
  }

  sending.value = true
  try {
    result.value = await callOpenService({
      path: endpoint.path,
      method: endpoint.method,
      apiKey: apiKey.value.trim(),
      payload,
    })
    if (isSuccess.value) {
      ElMessage.success(`调用成功，耗时 ${result.value.latencyMs} ms`)
      await auth.fetchMe().catch(() => null)
    } else {
      ElMessage.error(
        result.value.networkError ?? `调用失败（HTTP ${result.value.httpStatus}）`,
      )
    }
  } catch (error) {
    ElMessage.error(toErrorMessage(error))
  } finally {
    sending.value = false
  }
}

async function copySnippet(): Promise<void> {
  try {
    await copyText(codeSnippet.value)
    ElMessage.success('调用代码已复制')
  } catch (error) {
    ElMessage.error(toErrorMessage(error))
  }
}

async function copyResponse(): Promise<void> {
  if (!result.value) {
    return
  }
  try {
    await copyText(prettyResponse.value)
    ElMessage.success('响应 JSON 已复制')
  } catch (error) {
    ElMessage.error(toErrorMessage(error))
  }
}
</script>

<template>
  <div class="playground-page">
    <div class="page-head">
      <div>
        <h2>在线调试台</h2>
        <p>
          仅可选择已通过审批的开放接口。选择接口后会自动生成请求报文示例，可直接修改并发送；
          请求将真实扣减账户额度并写入调用日志。
        </p>
      </div>
      <div class="head-actions">
        <div class="balance-pill">
          剩余额度
          <strong>{{ auth.user ? auth.balanceQuota.toLocaleString('zh-CN') : '—' }}</strong>
          次
        </div>
        <el-button
          :icon="Refresh"
          :loading="loadingEndpoints"
          @click="loadEndpoints(false)"
        >
          刷新接口
        </el-button>
      </div>
    </div>

    <div class="playground-grid">
      <el-card shadow="never" class="panel request-panel">
        <template #header>
          <div class="panel-head">
            <div>
              <p class="panel-eyebrow">REQUEST</p>
              <h3>请求参数</h3>
            </div>
            <el-tag type="primary" effect="dark" class="method-tag">
              {{ selectedEndpoint ? requestMethod : 'POST' }}
            </el-tag>
          </div>
        </template>

        <el-form label-position="top" size="large" @submit.prevent="sendRequest">
          <el-form-item label="X-API-Key">
            <el-input
              v-model="apiKey"
              :prefix-icon="Key"
              type="password"
              show-password
              placeholder="sk-... （粘贴创建时保存的明文 Key）"
              autocomplete="off"
            />
            <p class="field-note">Key 仅保留在当前页面内存中，刷新后即清空。</p>
          </el-form-item>

          <el-form-item label="选择接口">
            <el-select
              v-model="selectedEndpointId"
              class="full-width"
              :loading="loadingEndpoints"
              :disabled="!hasSelectableEndpoints"
              placeholder="请选择已授权接口"
            >
              <el-option
                v-for="item in selectableEndpoints"
                :key="item.id"
                :value="item.id"
                :label="`${item.name} · ${item.method} ${item.path}`"
              />
            </el-select>
            <p v-if="selectedEndpoint" class="field-note">
              {{ selectedEndpoint.description || '暂无接口描述' }}
              <span class="cost-note">单次调用 {{ selectedEndpoint.discountedCost }} 点</span>
            </p>
            <p v-else-if="!loadingEndpoints" class="field-note">
              {{
                hasApprovedEndpoints
                  ? '已审批的接口当前均在维护中，请稍后再试。'
                  : '暂无可调试接口，仅展示当前账号已通过审批的开放接口。'
              }}
            </p>
          </el-form-item>

          <el-alert
            v-if="!loadingEndpoints && !hasSelectableEndpoints && !hasApprovedEndpoints"
            type="warning"
            :closable="false"
            show-icon
            class="permission-alert"
          >
            <template #title>
              当前账号还没有已通过审批的接口，
              <router-link class="inline-link" :to="{ name: 'market' }">
                前往 API 市场申请
              </router-link>
            </template>
          </el-alert>

          <el-alert
            v-else-if="!loadingEndpoints && !hasSelectableEndpoints"
            type="info"
            title="已审批的接口当前均在维护中，请稍后再试。"
            :closable="false"
            show-icon
            class="permission-alert"
          />

          <el-form-item v-if="selectedEndpoint" class="body-item">
            <template #label>
              <div class="label-row">
                <span>请求报文</span>
                <span class="label-actions">
                  <button type="button" class="link-button" @click="formatRequestBody">格式化</button>
                  <button type="button" class="link-button" @click="resetSample()">恢复示例</button>
                </span>
              </div>
            </template>
            <el-input
              v-model="requestBody"
              class="body-editor"
              type="textarea"
              :rows="9"
              spellcheck="false"
              aria-label="请求报文"
              placeholder="{ ... }"
            />
            <p class="field-note">
              已根据所选接口自动生成请求示例；POST 接口按此 JSON 原样发送，GET 接口会展开为 Query 参数。
            </p>
          </el-form-item>

          <div v-if="selectedEndpoint" class="request-preview">
            <span class="preview-method">{{ requestMethod }}</span>
            <code>{{ requestUrl }}</code>
          </div>

          <el-button
            type="primary"
            size="large"
            class="send-button"
            :icon="Promotion"
            :loading="sending"
            :disabled="!selectedEndpoint"
            @click="sendRequest"
          >
            {{ sending ? '请求中…' : '发送真实请求' }}
          </el-button>
        </el-form>
      </el-card>

      <el-card shadow="never" class="panel response-panel">
        <template #header>
          <div class="panel-head">
            <div>
              <p class="panel-eyebrow">RESPONSE</p>
              <h3>响应结果</h3>
            </div>
            <el-button
              :icon="CopyDocument"
              size="small"
              :disabled="!result"
              @click="copyResponse"
            >
              复制 JSON
            </el-button>
          </div>
        </template>

        <div v-if="!result" class="response-empty">
          <el-empty description="发送请求后，这里会展示状态码、耗时和完整 JSON 响应" />
        </div>

        <template v-else>
          <div class="metrics">
            <span :class="['status-chip', isSuccess ? 'is-ok' : 'is-bad']">
              HTTP {{ result.httpStatus || 'ERR' }}
            </span>
            <span class="metric">
              <span class="metric-label">耗时</span>
              <strong>{{ result.latencyMs }} ms</strong>
            </span>
            <span class="metric">
              <span class="metric-label">接口</span>
              <strong>{{ selectedEndpoint?.name ?? '—' }}</strong>
            </span>
          </div>

          <el-alert
            v-if="result.networkError"
            type="error"
            :title="result.networkError"
            :closable="false"
            show-icon
            class="network-alert"
          />

          <div class="response-json-wrap">
            <pre class="response-json">{{ prettyResponse }}</pre>
          </div>
        </template>
      </el-card>

      <el-card shadow="never" class="panel code-panel">
        <template #header>
          <div class="panel-head">
            <div>
              <p class="panel-eyebrow">CODE</p>
              <h3>自动生成调用代码</h3>
            </div>
            <div class="code-actions">
              <div class="code-tabs">
                <button
                  type="button"
                  :class="['code-tab', { active: codeTab === 'curl' }]"
                  @click="codeTab = 'curl'"
                >
                  cURL
                </button>
                <button
                  type="button"
                  :class="['code-tab', { active: codeTab === 'python' }]"
                  @click="codeTab = 'python'"
                >
                  Python
                </button>
              </div>
              <el-button
                :icon="CopyDocument"
                size="small"
                :disabled="!codeSnippet"
                @click="copySnippet"
              >
                复制代码
              </el-button>
            </div>
          </div>
        </template>

        <pre v-if="codeSnippet" class="code-block"><code>{{ codeSnippet }}</code></pre>
        <el-empty v-else description="选择接口后即可生成调用代码" />
        <p class="code-note">
          代码中的 Key 为当前输入值。请勿将真实 Key 提交到代码仓库或公开分享。
        </p>
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.playground-page {
  display: grid;
  gap: 1.25rem;
}

.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1.5rem;
}

.page-head h2 {
  margin: 0;
  font-size: 1.35rem;
  font-weight: 700;
  color: #0f172a;
}

.page-head p {
  max-width: 48rem;
  margin: 0.45rem 0 0;
  font-size: 0.85rem;
  line-height: 1.7;
  color: #64748b;
}

.head-actions {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 0.75rem;
}

.balance-pill {
  flex-shrink: 0;
  padding: 0.45rem 0.8rem;
  border: 1px solid #e2e8f0;
  border-radius: 999px;
  background: #ffffff;
  font-size: 0.76rem;
  color: #64748b;
}

.balance-pill strong {
  margin: 0 0.2rem;
  color: #4338ca;
}

.playground-grid {
  display: grid;
  grid-template-columns: minmax(0, 0.92fr) minmax(0, 1.08fr);
  gap: 1.25rem;
  align-items: start;
}

.panel {
  border: 1px solid #e2e8f0;
  border-radius: 14px;
}

.code-panel {
  grid-column: 1 / -1;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.panel-eyebrow {
  margin: 0 0 0.2rem;
  font-size: 0.64rem;
  letter-spacing: 0.18em;
  color: #94a3b8;
}

.panel-head h3 {
  margin: 0;
  font-size: 1rem;
  font-weight: 700;
  color: #0f172a;
}

.method-tag {
  letter-spacing: 0.06em;
}

.full-width {
  width: 100%;
}

.field-note {
  margin: 0.4rem 0 0;
  font-size: 0.72rem;
  line-height: 1.5;
  color: #94a3b8;
}

.cost-note {
  margin-left: 0.5rem;
  color: #4338ca;
  font-weight: 600;
}

.permission-alert {
  margin-bottom: 1rem;
}

.inline-link {
  color: #4338ca;
  font-weight: 600;
  text-decoration: underline;
}

.label-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.label-actions {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.link-button {
  padding: 0;
  border: 0;
  background: transparent;
  color: #4338ca;
  font-size: 0.72rem;
  font-weight: 600;
  cursor: pointer;
}

.link-button:hover {
  color: #3730a3;
  text-decoration: underline;
}

.body-editor :deep(.el-textarea__inner) {
  font-family: 'JetBrains Mono', 'SFMono-Regular', Consolas, monospace;
  font-size: 0.82rem;
  line-height: 1.7;
  background: #f8fafc;
}

.request-preview {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  margin: 0.35rem 0 1rem;
  padding: 0.65rem 0.75rem;
  border: 1px dashed #cbd5e1;
  border-radius: 9px;
  background: #f8fafc;
  overflow: hidden;
}

.preview-method {
  flex-shrink: 0;
  padding: 0.12rem 0.4rem;
  border-radius: 4px;
  background: #e0e7ff;
  color: #4338ca;
  font-size: 0.68rem;
  font-weight: 700;
}

.request-preview code {
  overflow: hidden;
  font-size: 0.72rem;
  color: #475569;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.send-button {
  width: 100%;
}

.response-empty {
  padding: 2rem 0;
}

.metrics {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 1rem;
  margin-bottom: 1rem;
}

.status-chip {
  padding: 0.25rem 0.6rem;
  border-radius: 999px;
  font-size: 0.74rem;
  font-weight: 700;
  letter-spacing: 0.03em;
}

.status-chip.is-ok {
  background: #ecfdf5;
  color: #047857;
}

.status-chip.is-bad {
  background: #fef2f2;
  color: #b91c1c;
}

.metric {
  display: grid;
  gap: 0.1rem;
}

.metric-label {
  font-size: 0.64rem;
  letter-spacing: 0.08em;
  color: #94a3b8;
}

.metric strong {
  font-size: 0.82rem;
  color: #334155;
}

.network-alert {
  margin-bottom: 0.9rem;
}

.response-json-wrap {
  max-height: 430px;
  overflow: auto;
  border: 1px solid #1e293b;
  border-radius: 10px;
  background: #0b1222;
}

.response-json {
  margin: 0;
  padding: 1rem 1.1rem;
  font-size: 0.76rem;
  line-height: 1.65;
  color: #a5f3fc;
  white-space: pre-wrap;
  word-break: break-word;
}

.code-actions {
  display: flex;
  align-items: center;
  gap: 0.6rem;
}

.code-tabs {
  display: inline-grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 0.2rem;
  padding: 0.2rem;
  border-radius: 8px;
  background: #f1f5f9;
}

.code-tab {
  padding: 0.32rem 0.75rem;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #64748b;
  font-size: 0.74rem;
  font-weight: 600;
  cursor: pointer;
}

.code-tab.active {
  background: #ffffff;
  color: #4338ca;
  box-shadow: 0 1px 4px rgba(15, 23, 42, 0.08);
}

.code-block {
  margin: 0;
  padding: 1.15rem 1.25rem;
  overflow-x: auto;
  border-radius: 10px;
  background: #0b1222;
  font-size: 0.78rem;
  line-height: 1.7;
  color: #7dd3fc;
}

.code-note {
  margin: 0.7rem 0 0;
  font-size: 0.72rem;
  color: #94a3b8;
}

@media (max-width: 1080px) {
  .playground-grid {
    grid-template-columns: 1fr;
  }

  .code-panel {
    grid-column: auto;
  }
}

@media (max-width: 720px) {
  .page-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .head-actions {
    width: 100%;
    justify-content: space-between;
  }
}
</style>

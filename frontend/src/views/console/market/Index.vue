<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  CopyDocument,
  Document,
  Goods,
  Promotion,
  Refresh,
  Search,
} from '@element-plus/icons-vue'
import {
  applyPermission,
  getMarket,
  type EndpointMarketItem,
  type PermissionStatus,
} from '@/api/permission'
import { copyText } from '@/utils/clipboard'
import { toErrorMessage } from '@/utils/error'
import { buildRequestSample, formatSample } from '@/utils/requestSample'

defineOptions({ name: 'ApiMarketView' })

interface StatusMeta {
  label: string
  className: string
}

type StatusFilter = 'all' | 'none' | 'pending' | 'approved' | 'rejected'

interface FilterOption {
  value: StatusFilter
  label: string
  className: string
  count: number
}

const router = useRouter()

const endpoints = ref<EndpointMarketItem[]>([])
const loading = ref(false)
const keyword = ref('')
const statusFilter = ref<StatusFilter>('all')

const applyVisible = ref(false)
const applyTarget = ref<EndpointMarketItem | null>(null)
const applyReason = ref('')
const applying = ref(false)

const detailVisible = ref(false)
const detailTarget = ref<EndpointMarketItem | null>(null)

/** 按申请状态 + 接口名称双重过滤，两者同时生效。 */
const filteredEndpoints = computed(() => {
  const normalized = keyword.value.trim().toLowerCase()
  return endpoints.value.filter((item) => {
    if (!matchesStatusFilter(item.applyStatus)) {
      return false
    }
    return !normalized || item.name.toLowerCase().includes(normalized)
  })
})

const filterOptions = computed<FilterOption[]>(() => [
  { value: 'all', label: '全部', className: 'is-all', count: endpoints.value.length },
  { value: 'none', label: '未申请', className: 'is-none', count: countByStatus(null) },
  { value: 'pending', label: '待审批', className: 'is-pending', count: countByStatus(0) },
  { value: 'approved', label: '已通过', className: 'is-approved', count: countByStatus(1) },
  {
    value: 'rejected',
    label: '未通过',
    className: 'is-rejected',
    count: endpoints.value.filter((item) => item.applyStatus === 2 || item.applyStatus === 3).length,
  },
])

const isFiltered = computed(
  () => statusFilter.value !== 'all' || keyword.value.trim().length > 0,
)

const detailRequestSample = computed(() => {
  if (!detailTarget.value) {
    return ''
  }
  return formatSample(buildRequestSample(detailTarget.value))
})

const detailResponseSample = computed(() => prettyJson(detailTarget.value?.responseSchema ?? null))

const detailActionLabel = computed(() => {
  if (!detailTarget.value) {
    return ''
  }
  switch (detailTarget.value.applyStatus) {
    case null:
      return '申请调用权限'
    case 1:
      return '前往调试'
    case 2:
    case 3:
      return '重新申请'
    default:
      return ''
  }
})

function matchesStatusFilter(status: PermissionStatus | null): boolean {
  switch (statusFilter.value) {
    case 'none':
      return status === null
    case 'pending':
      return status === 0
    case 'approved':
      return status === 1
    case 'rejected':
      return status === 2 || status === 3
    default:
      return true
  }
}

function countByStatus(status: PermissionStatus | null): number {
  return endpoints.value.filter((item) => item.applyStatus === status).length
}

function resetFilters(): void {
  statusFilter.value = 'all'
  keyword.value = ''
}

/** 再次点击已选中的筛选项可取消，回到「全部」。 */
function selectStatus(value: StatusFilter): void {
  statusFilter.value = statusFilter.value === value && value !== 'all' ? 'all' : value
}

onMounted(() => {
  void loadMarket()
})

async function loadMarket(): Promise<void> {
  loading.value = true
  try {
    endpoints.value = await getMarket()
  } catch (error) {
    ElMessage.error(toErrorMessage(error))
  } finally {
    loading.value = false
  }
}

function openApply(endpoint: EndpointMarketItem): void {
  applyTarget.value = endpoint
  applyReason.value = ''
  applyVisible.value = true
}

function openDetail(endpoint: EndpointMarketItem): void {
  detailTarget.value = endpoint
  detailVisible.value = true
}

function handleDetailAction(): void {
  const target = detailTarget.value
  if (!target) {
    return
  }
  detailVisible.value = false
  if (target.applyStatus === 1) {
    goPlayground(target)
    return
  }
  openApply(target)
}

async function copyDetailSample(): Promise<void> {
  try {
    await copyText(detailRequestSample.value)
    ElMessage.success('请求示例已复制')
  } catch (error) {
    ElMessage.error(toErrorMessage(error))
  }
}

function prettyJson(raw: string | null): string {
  if (!raw || !raw.trim()) {
    return ''
  }
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw.trim()
  }
}

function formatDateTime(value: string | null): string {
  if (!value) {
    return '尚未提交申请'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return date.toLocaleString('zh-CN', { hour12: false })
}

async function submitApply(): Promise<void> {
  const target = applyTarget.value
  if (!target) {
    return
  }
  const reason = applyReason.value.trim()
  if (!reason) {
    ElMessage.warning('请填写申请理由与业务场景')
    return
  }

  applying.value = true
  try {
    await applyPermission({ endpointId: target.id, applyReason: reason })
    applyVisible.value = false
    ElMessage.success('申请已提交，请等待管理员审批')
    await loadMarket()
  } catch (error) {
    ElMessage.error(toErrorMessage(error))
  } finally {
    applying.value = false
  }
}

function goPlayground(endpoint: EndpointMarketItem): void {
  void router.push({ name: 'playground', query: { endpointId: endpoint.id } })
}

function statusMeta(status: PermissionStatus | null): StatusMeta {
  switch (status) {
    case null:
      return { label: '未申请', className: 'is-none' }
    case 0:
      return { label: '待审批', className: 'is-pending' }
    case 1:
      return { label: '已通过', className: 'is-approved' }
    case 2:
      return { label: '已驳回', className: 'is-rejected' }
    case 3:
      return { label: '已收回', className: 'is-revoked' }
  }
}

function methodClass(method: string): string {
  return `method-${method.toLowerCase()}`
}

function discountLabel(rate: number): string {
  if (rate <= 0) {
    return '免费特权'
  }
  const tenths = Math.round(rate * 100) / 10
  return `${Number.isInteger(tenths) ? tenths : tenths.toFixed(1)} 折`
}

function formatPoints(value: number): string {
  return value.toLocaleString('zh-CN')
}
</script>

<template>
  <div class="market-page">
    <div class="page-head">
      <div>
        <div class="title-row">
          <span class="title-icon"><el-icon :size="20"><Goods /></el-icon></span>
          <h2>API 市场</h2>
        </div>
        <p>
          浏览平台提供的全部接口，提交调用权限申请。通过审批后即可前往在线调试台调用；
          若管理员设置了专属折扣，实际扣费按「基础点数 × 折扣率」向上取整。
        </p>
      </div>
      <div class="head-actions">
        <div class="status-filters" role="group" aria-label="按申请状态筛选">
          <button
            v-for="option in filterOptions"
            :key="option.value"
            type="button"
            :class="['stat-pill', option.className, { 'is-active': statusFilter === option.value }]"
            :aria-pressed="statusFilter === option.value"
            @click="selectStatus(option.value)"
          >
            <span>{{ option.label }}</span>
            <strong>{{ option.count }}</strong>
          </button>
        </div>
        <el-button :icon="Refresh" :loading="loading" @click="loadMarket">刷新</el-button>
      </div>
    </div>

    <div class="filter-bar">
      <el-input
        v-model="keyword"
        :prefix-icon="Search"
        clearable
        placeholder="搜索 API 名称"
        class="search-input"
      />
      <span class="search-summary">
        共 {{ endpoints.length }} 个接口
        <template v-if="isFiltered">
          ，筛选出 <strong>{{ filteredEndpoints.length }}</strong> 个
        </template>
      </span>
    </div>

    <div v-loading="loading" class="market-body">
      <div
        v-if="!loading && endpoints.length === 0"
        class="empty-panel"
      >
        <el-empty description="暂无可用接口，请稍后再试" />
      </div>

      <div v-else-if="!loading && filteredEndpoints.length === 0" class="empty-panel">
        <el-empty description="当前筛选条件下没有匹配的接口">
          <el-button @click="resetFilters">重置筛选</el-button>
        </el-empty>
      </div>

      <div v-else class="grid grid-cols-1 gap-5 md:grid-cols-2 2xl:grid-cols-3">
        <article
          v-for="endpoint in filteredEndpoints"
          :key="endpoint.id"
          class="group flex min-h-[300px] flex-col rounded-2xl border border-slate-200 bg-white p-5 shadow-sm transition duration-200 hover:-translate-y-0.5 hover:border-indigo-200 hover:shadow-lg"
        >
          <div class="flex items-start justify-between gap-3">
            <div class="flex flex-wrap items-center gap-2">
              <span :class="['method-tag', methodClass(endpoint.method)]">
                {{ endpoint.method }}
              </span>
              <span v-if="endpoint.endpointStatus !== 1" class="maintenance-tag">维护中</span>
            </div>
            <span :class="['status-chip', statusMeta(endpoint.applyStatus).className]">
              {{ statusMeta(endpoint.applyStatus).label }}
            </span>
          </div>

          <h3 class="mt-3 text-base font-semibold text-slate-900">{{ endpoint.name }}</h3>
          <code class="mt-1 block truncate text-xs text-slate-400">{{ endpoint.path }}</code>
          <p class="mt-2 line-clamp-2 text-sm leading-6 text-slate-500">
            {{ endpoint.description || '暂无接口描述' }}
          </p>

          <div class="mt-4 flex items-end justify-between rounded-xl bg-slate-50 px-3 py-2.5">
            <div>
              <p class="text-[11px] uppercase tracking-wider text-slate-400">单次调用</p>
              <div class="flex items-baseline gap-2">
                <strong class="text-xl font-bold text-slate-900">
                  {{ formatPoints(endpoint.discountedCost) }}
                </strong>
                <span class="text-xs text-slate-500">点</span>
                <span
                  v-if="endpoint.applyStatus === 1 && endpoint.discountRate < 1"
                  class="text-xs text-slate-400 line-through"
                >
                  {{ formatPoints(endpoint.baseCost) }}
                </span>
              </div>
            </div>
            <el-tag
              v-if="endpoint.applyStatus === 1 && endpoint.discountRate < 1"
              type="success"
              effect="plain"
              size="small"
              round
            >
              {{ discountLabel(endpoint.discountRate) }}
            </el-tag>
            <span v-else class="text-xs text-slate-400">基础点数</span>
          </div>

          <div
            v-if="endpoint.applyStatus === 2 || endpoint.applyStatus === 3"
            class="mt-3 rounded-lg border border-rose-100 bg-rose-50 px-3 py-2"
          >
            <p class="text-[11px] font-semibold text-rose-500">
              {{ endpoint.applyStatus === 3 ? '权限收回原因' : '驳回原因' }}
            </p>
            <p class="mt-0.5 text-xs leading-5 text-rose-700">
              {{ endpoint.auditRemark || '管理员未填写审批意见' }}
            </p>
          </div>

          <div class="card-actions mt-auto pt-4">
            <el-button :icon="Document" @click="openDetail(endpoint)">详情</el-button>
            <el-button
              v-if="endpoint.applyStatus === null"
              type="primary"
              class="action-button"
              :icon="Promotion"
              @click="openApply(endpoint)"
            >
              申请调用权限
            </el-button>
            <el-button v-else-if="endpoint.applyStatus === 0" class="action-button" disabled>
              待审批
            </el-button>
            <el-button
              v-else-if="endpoint.applyStatus === 1"
              type="primary"
              plain
              class="action-button"
              @click="goPlayground(endpoint)"
            >
              前往调试
            </el-button>
            <el-button v-else class="action-button" @click="openApply(endpoint)">
              重新申请
            </el-button>
          </div>
        </article>
      </div>
    </div>

    <el-dialog
      v-model="applyVisible"
      title="申请接口权限"
      width="520px"
      :close-on-click-modal="false"
    >
      <template v-if="applyTarget">
        <div class="apply-summary">
          <div class="flex items-center justify-between gap-3">
            <div>
              <p class="text-sm font-semibold text-slate-900">{{ applyTarget.name }}</p>
              <code class="mt-1 block text-xs text-slate-400">{{ applyTarget.path }}</code>
            </div>
            <span :class="['method-tag', methodClass(applyTarget.method)]">
              {{ applyTarget.method }}
            </span>
          </div>
          <div class="mt-3 flex items-center justify-between text-xs text-slate-500">
            <span>基础点数</span>
            <strong class="text-slate-900">{{ formatPoints(applyTarget.baseCost) }} 点 / 次</strong>
          </div>
        </div>

        <el-form label-position="top" class="mt-4">
          <el-form-item label="申请理由与业务场景">
            <el-input
              v-model="applyReason"
              type="textarea"
              :rows="5"
              maxlength="512"
              show-word-limit
              placeholder="请说明使用场景、预估调用量以及需要的调用频率，便于管理员评估"
            />
          </el-form-item>
        </el-form>

        <el-alert type="info" :closable="false" show-icon>
          审批通过后，管理员可能为该接口设置专属折扣，折后点数会实时展示在接口卡片上。
        </el-alert>
      </template>

      <template #footer>
        <el-button @click="applyVisible = false">取消</el-button>
        <el-button type="primary" :loading="applying" :disabled="!applyReason.trim()" @click="submitApply">
          提交申请
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="detailVisible"
      title="接口详情"
      width="min(680px, 92vw)"
      :close-on-click-modal="false"
    >
      <template v-if="detailTarget">
        <div class="detail-head">
          <div class="flex flex-wrap items-center gap-2">
            <span :class="['method-tag', methodClass(detailTarget.method)]">
              {{ detailTarget.method }}
            </span>
            <span v-if="detailTarget.endpointStatus !== 1" class="maintenance-tag">维护中</span>
            <span :class="['status-chip', statusMeta(detailTarget.applyStatus).className]">
              {{ statusMeta(detailTarget.applyStatus).label }}
            </span>
          </div>
          <h3>{{ detailTarget.name }}</h3>
          <code>{{ detailTarget.path }}</code>
        </div>

        <p class="detail-desc">{{ detailTarget.description || '暂无接口描述' }}</p>

        <div class="detail-metrics">
          <div class="detail-metric">
            <span>单次调用</span>
            <strong>{{ formatPoints(detailTarget.discountedCost) }} 点</strong>
            <em v-if="detailTarget.applyStatus === 1 && detailTarget.discountRate < 1">
              原价 {{ formatPoints(detailTarget.baseCost) }} 点 ·
              {{ discountLabel(detailTarget.discountRate) }}
            </em>
            <em v-else>基础点数 {{ formatPoints(detailTarget.baseCost) }} 点</em>
          </div>
          <div class="detail-metric">
            <span>接口状态</span>
            <strong>{{ detailTarget.endpointStatus === 1 ? '运行中' : '维护中' }}</strong>
            <em>{{ detailTarget.method }} 接口</em>
          </div>
          <div class="detail-metric">
            <span>申请状态</span>
            <strong>{{ statusMeta(detailTarget.applyStatus).label }}</strong>
            <em>{{ formatDateTime(detailTarget.applyTime) }}</em>
          </div>
        </div>

        <div class="detail-section">
          <div class="detail-section-head">
            <div>
              <p class="section-eyebrow">REQUEST SAMPLE</p>
              <h4>示例请求报文</h4>
            </div>
            <el-button :icon="CopyDocument" size="small" @click="copyDetailSample">
              复制
            </el-button>
          </div>
          <pre class="detail-code">{{ detailRequestSample }}</pre>
          <p class="detail-note">
            示例依据接口目录的 request_schema 生成，正式调用时请替换为真实业务参数。
          </p>
        </div>

        <div v-if="detailResponseSample" class="detail-section">
          <div class="detail-section-head">
            <div>
              <p class="section-eyebrow">RESPONSE SAMPLE</p>
              <h4>示例响应报文</h4>
            </div>
          </div>
          <pre class="detail-code is-response">{{ detailResponseSample }}</pre>
        </div>
      </template>

      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button v-if="detailActionLabel" type="primary" @click="handleDetailAction">
          {{ detailActionLabel }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.market-page {
  display: grid;
  gap: 1.25rem;
}

.page-head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1rem;
}

.title-row {
  display: flex;
  align-items: center;
  gap: 0.6rem;
}

.title-icon {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  background: #eef2ff;
  color: #4338ca;
}

.page-head h2 {
  margin: 0;
  font-size: 1.35rem;
  font-weight: 700;
  color: #0f172a;
}

.page-head p {
  max-width: 54rem;
  margin: 0.5rem 0 0;
  font-size: 0.85rem;
  line-height: 1.7;
  color: #64748b;
}

.head-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.6rem;
}

.status-filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.4rem;
}

.stat-pill {
  display: inline-flex;
  align-items: baseline;
  gap: 0.35rem;
  padding: 0.42rem 0.7rem;
  border: 1px solid #e2e8f0;
  border-radius: 999px;
  background: #ffffff;
  font: inherit;
  font-size: 0.72rem;
  color: #64748b;
  cursor: pointer;
  transition:
    border-color 0.15s ease,
    background 0.15s ease,
    color 0.15s ease,
    box-shadow 0.15s ease;
}

.stat-pill:hover {
  border-color: #c7d2fe;
  background: #f8fafc;
}

.stat-pill:focus-visible {
  outline: none;
  box-shadow: 0 0 0 2px rgba(79, 70, 229, 0.24);
}

.stat-pill strong {
  font-size: 0.86rem;
  color: #0f172a;
}

.stat-pill.is-all {
  color: #4338ca;
}

.stat-pill.is-pending {
  color: #b45309;
}

.stat-pill.is-approved {
  color: #047857;
}

.stat-pill.is-rejected {
  color: #b91c1c;
}

.stat-pill.is-active {
  border-color: #4338ca;
  background: #eef2ff;
  color: #3730a3;
  box-shadow: inset 0 0 0 1px rgba(67, 56, 202, 0.35);
}

.stat-pill.is-all.is-active {
  background: #eef2ff;
}

.stat-pill.is-none.is-active {
  border-color: #94a3b8;
  background: #f1f5f9;
  color: #334155;
  box-shadow: inset 0 0 0 1px rgba(100, 116, 139, 0.35);
}

.stat-pill.is-pending.is-active {
  border-color: #f59e0b;
  background: #fffbeb;
  color: #b45309;
  box-shadow: inset 0 0 0 1px rgba(245, 158, 11, 0.35);
}

.stat-pill.is-approved.is-active {
  border-color: #10b981;
  background: #ecfdf5;
  color: #047857;
  box-shadow: inset 0 0 0 1px rgba(16, 185, 129, 0.35);
}

.stat-pill.is-rejected.is-active {
  border-color: #f43f5e;
  background: #fef2f2;
  color: #b91c1c;
  box-shadow: inset 0 0 0 1px rgba(244, 63, 94, 0.35);
}

.market-body {
  min-height: 200px;
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem;
}

.search-input {
  flex: 1 1 280px;
  max-width: 380px;
}

.search-summary {
  font-size: 0.76rem;
  color: #94a3b8;
}

.search-summary strong {
  color: #4338ca;
}

.empty-panel {
  padding: 4rem 1.5rem;
  border: 1px dashed #e2e8f0;
  border-radius: 16px;
  background: #ffffff;
}

.card-actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.card-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.action-button {
  flex: 1;
}

.method-tag {
  display: inline-flex;
  align-items: center;
  padding: 0.14rem 0.45rem;
  border-radius: 5px;
  background: #f1f5f9;
  color: #475569;
  font-size: 0.66rem;
  font-weight: 700;
  letter-spacing: 0.05em;
}

.method-tag.method-get {
  background: #eff6ff;
  color: #1d4ed8;
}

.method-tag.method-post {
  background: #ecfdf5;
  color: #047857;
}

.method-tag.method-put,
.method-tag.method-patch {
  background: #fff7ed;
  color: #c2410c;
}

.method-tag.method-delete {
  background: #fef2f2;
  color: #b91c1c;
}

.maintenance-tag {
  padding: 0.12rem 0.4rem;
  border-radius: 5px;
  background: #fff7ed;
  color: #c2410c;
  font-size: 0.64rem;
  font-weight: 600;
}

.status-chip {
  flex-shrink: 0;
  padding: 0.18rem 0.55rem;
  border-radius: 999px;
  font-size: 0.68rem;
  font-weight: 600;
  white-space: nowrap;
}

.status-chip.is-none {
  background: #f1f5f9;
  color: #64748b;
}

.status-chip.is-pending {
  background: #fffbeb;
  color: #b45309;
}

.status-chip.is-approved {
  background: #ecfdf5;
  color: #047857;
}

.status-chip.is-rejected {
  background: #fef2f2;
  color: #b91c1c;
}

.status-chip.is-revoked {
  background: #f5f3ff;
  color: #6d28d9;
}

.apply-summary {
  padding: 0.9rem 1rem;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #f8fafc;
}

.detail-head {
  padding: 1rem 1.1rem;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #f8fafc;
}

.detail-head h3 {
  margin: 0.6rem 0 0.25rem;
  font-size: 1.05rem;
  font-weight: 700;
  color: #0f172a;
}

.detail-head code {
  display: block;
  font-size: 0.75rem;
  color: #64748b;
  word-break: break-all;
}

.detail-desc {
  margin: 0.85rem 0 0;
  font-size: 0.83rem;
  line-height: 1.7;
  color: #475569;
}

.detail-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0.75rem;
  margin-top: 1rem;
}

.detail-metric {
  display: grid;
  align-content: start;
  gap: 0.22rem;
  padding: 0.7rem 0.8rem;
  border: 1px solid #eef2f7;
  border-radius: 10px;
  background: #ffffff;
}

.detail-metric span {
  font-size: 0.68rem;
  color: #94a3b8;
}

.detail-metric strong {
  font-size: 0.9rem;
  color: #0f172a;
}

.detail-metric em {
  font-size: 0.68rem;
  font-style: normal;
  line-height: 1.45;
  color: #94a3b8;
}

.detail-section {
  margin-top: 1.1rem;
}

.detail-section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 0.55rem;
}

.section-eyebrow {
  margin: 0 0 0.15rem;
  font-size: 0.62rem;
  letter-spacing: 0.16em;
  color: #94a3b8;
}

.detail-section-head h4 {
  margin: 0;
  font-size: 0.9rem;
  font-weight: 700;
  color: #0f172a;
}

.detail-code {
  max-height: 280px;
  margin: 0;
  padding: 1rem 1.1rem;
  overflow: auto;
  border-radius: 10px;
  background: #0b1222;
  font-family: 'JetBrains Mono', 'SFMono-Regular', Consolas, monospace;
  font-size: 0.76rem;
  line-height: 1.65;
  color: #a5f3fc;
  white-space: pre-wrap;
  word-break: break-word;
}

.detail-code.is-response {
  color: #c4b5fd;
}

.detail-note {
  margin: 0.5rem 0 0;
  font-size: 0.7rem;
  line-height: 1.5;
  color: #94a3b8;
}

@media (max-width: 720px) {
  .page-head {
    align-items: flex-start;
  }

  .head-actions {
    width: 100%;
  }

  .search-input {
    max-width: none;
  }

  .detail-metrics {
    grid-template-columns: 1fr;
  }
}
</style>

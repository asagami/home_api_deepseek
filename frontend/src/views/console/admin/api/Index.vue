<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  CircleCheck,
  CircleClose,
  EditPen,
  Lock,
  PriceTag,
  Promotion,
  Refresh,
  Search,
  Stamp,
} from '@element-plus/icons-vue'
import {
  auditPermission,
  getMarket,
  listAdminEndpoints,
  listAdminPermissions,
  updateEndpointCost,
  updateEndpointStatus,
  type AdminEndpointItem,
  type AdminPermissionItem,
  type EndpointMarketItem,
  type PermissionAuditAction,
  type PermissionStatus,
} from '@/api/permission'
import { toErrorMessage } from '@/utils/error'

defineOptions({ name: 'AdminApiManageView' })

type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'

interface AuditForm {
  action: PermissionAuditAction
  customDiscount: number
  auditRemark: string
}

interface ActionOption {
  value: PermissionAuditAction
  label: string
}

interface PricingRow extends EndpointMarketItem {
  draftCost: number
}

const DISCOUNT_PRESETS = [
  { label: '原价', value: 1 },
  { label: '9 折', value: 0.9 },
  { label: '8 折', value: 0.8 },
  { label: '5 折', value: 0.5 },
  { label: '免费', value: 0 },
]

const activeTab = ref('tickets')

const tickets = ref<AdminPermissionItem[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)
const statusFilter = ref<'' | '0' | '1' | '2' | '3'>('')
const keyword = ref('')

const auditVisible = ref(false)
const auditTarget = ref<AdminPermissionItem | null>(null)
const auditForm = reactive<AuditForm>({
  action: 'APPROVE',
  customDiscount: 1,
  auditRemark: '',
})
const auditing = ref(false)

const pricingRows = ref<PricingRow[]>([])
const pricingLoading = ref(false)
const pricingLoaded = ref(false)
const savingId = ref<string | null>(null)

const publishRows = ref<AdminEndpointItem[]>([])
const publishLoading = ref(false)
const publishLoaded = ref(false)
const publishStatusFilter = ref<'' | '0' | '1'>('')
const publishKeyword = ref('')
const togglingId = ref<string | null>(null)

const availableActions = computed<ActionOption[]>(() => {
  const target = auditTarget.value
  if (!target) {
    return []
  }
  if (target.status === 0) {
    return [
      { value: 'APPROVE', label: '通过申请' },
      { value: 'REJECT', label: '驳回申请' },
    ]
  }
  if (target.status === 1) {
    return [{ value: 'REVOKE', label: '收回权限' }]
  }
  return []
})

const auditActualCost = computed(() => {
  const target = auditTarget.value
  if (!target) {
    return 0
  }
  if (auditForm.action !== 'APPROVE') {
    return target.actualCost
  }
  return discountedPoints(target.baseCost, auditForm.customDiscount)
})

const auditSubmitLabel = computed(() => {
  switch (auditForm.action) {
    case 'APPROVE':
      return '确认通过'
    case 'REJECT':
      return '确认驳回'
    case 'REVOKE':
      return '确认收回'
    default:
      return '确认提交'
  }
})

onMounted(() => {
  void loadTickets()
})

watch(activeTab, (tab) => {
  if (tab === 'pricing' && !pricingLoaded.value) {
    void loadPricing()
  }
  if (tab === 'publish' && !publishLoaded.value) {
    void loadPublish()
  }
})

async function loadTickets(): Promise<void> {
  loading.value = true
  try {
    const result = await listAdminPermissions({
      page: page.value,
      size: size.value,
      status: statusFilter.value === '' ? undefined : Number(statusFilter.value),
      keyword: keyword.value.trim() || undefined,
    })
    tickets.value = result.records
    total.value = result.total
    page.value = result.pageNum
  } catch (error) {
    ElMessage.error(toErrorMessage(error))
  } finally {
    loading.value = false
  }
}

function applyFilters(): void {
  page.value = 1
  void loadTickets()
}

function resetFilters(): void {
  statusFilter.value = ''
  keyword.value = ''
  page.value = 1
  void loadTickets()
}

function handlePageChange(value: number): void {
  page.value = value
  void loadTickets()
}

function handleSizeChange(value: number): void {
  size.value = value
  page.value = 1
  void loadTickets()
}

async function loadPricing(): Promise<void> {
  pricingLoading.value = true
  try {
    const market = await getMarket()
    pricingRows.value = market.map((item) => ({ ...item, draftCost: item.baseCost }))
    pricingLoaded.value = true
  } catch (error) {
    ElMessage.error(toErrorMessage(error))
  } finally {
    pricingLoading.value = false
  }
}

async function loadPublish(): Promise<void> {
  publishLoading.value = true
  try {
    publishRows.value = await listAdminEndpoints({
      status: publishStatusFilter.value === '' ? undefined : Number(publishStatusFilter.value),
      keyword: publishKeyword.value.trim() || undefined,
    })
    publishLoaded.value = true
  } catch (error) {
    ElMessage.error(toErrorMessage(error))
  } finally {
    publishLoading.value = false
  }
}

function applyPublishFilters(): void {
  void loadPublish()
}

function resetPublishFilters(): void {
  publishStatusFilter.value = ''
  publishKeyword.value = ''
  void loadPublish()
}

async function togglePublish(row: AdminEndpointItem): Promise<void> {
  const nextStatus: 0 | 1 = row.endpointStatus === 1 ? 0 : 1
  if (nextStatus === 0) {
    try {
      await ElMessageBox.confirm(
        `下线后「${row.name}」将立即停止对外服务，调用方会收到「API 已下线」，是否继续？`,
        '确认下线接口',
        { type: 'warning', confirmButtonText: '确认下线', cancelButtonText: '取消' },
      )
    } catch {
      return
    }
  }

  togglingId.value = row.id
  try {
    const updated = await updateEndpointStatus(row.id, nextStatus)
    row.endpointStatus = updated.endpointStatus
    ElMessage.success(
      nextStatus === 1 ? `「${row.name}」已上线` : `「${row.name}」已下线`,
    )
  } catch (error) {
    ElMessage.error(toErrorMessage(error))
  } finally {
    togglingId.value = null
  }
}

function refreshActive(): void {
  if (activeTab.value === 'pricing') {
    void loadPricing()
  } else if (activeTab.value === 'publish') {
    void loadPublish()
  } else {
    void loadTickets()
  }
}

const refreshLoading = computed(() => {
  if (activeTab.value === 'pricing') {
    return pricingLoading.value
  }
  if (activeTab.value === 'publish') {
    return publishLoading.value
  }
  return loading.value
})

function openAudit(row: AdminPermissionItem): void {
  if (row.status !== 0 && row.status !== 1) {
    return
  }
  auditTarget.value = row
  auditForm.action = row.status === 1 ? 'REVOKE' : 'APPROVE'
  auditForm.customDiscount = row.status === 1 ? row.customDiscount : 1
  auditForm.auditRemark = ''
  auditVisible.value = true
}

function setDiscount(value: number | undefined): void {
  const next = Math.min(1, Math.max(0, Math.round((value ?? 0) * 100) / 100))
  auditForm.customDiscount = next
}

async function submitAudit(): Promise<void> {
  const target = auditTarget.value
  if (!target) {
    return
  }
  if (auditForm.action === 'REJECT' && !auditForm.auditRemark.trim()) {
    ElMessage.warning('驳回申请时必须填写审批意见')
    return
  }

  auditing.value = true
  try {
    await auditPermission(target.id, {
      action: auditForm.action,
      customDiscount: auditForm.action === 'APPROVE' ? auditForm.customDiscount : undefined,
      auditRemark: auditForm.auditRemark.trim() || undefined,
    })
    auditVisible.value = false
    ElMessage.success(auditSuccessText(auditForm.action))
    await loadTickets()
  } catch (error) {
    ElMessage.error(toErrorMessage(error))
  } finally {
    auditing.value = false
  }
}

async function savePrice(row: PricingRow): Promise<void> {
  if (row.draftCost === row.baseCost) {
    return
  }
  if (!Number.isFinite(row.draftCost) || row.draftCost < 0) {
    ElMessage.warning('基础点数不能小于 0')
    return
  }

  const cost = Math.round(row.draftCost)
  savingId.value = row.id
  try {
    await updateEndpointCost(row.id, cost)
    row.baseCost = cost
    row.draftCost = cost
    ElMessage.success(`「${row.name}」基础点数已更新为 ${cost} 点`)
  } catch (error) {
    ElMessage.error(toErrorMessage(error))
  } finally {
    savingId.value = null
  }
}

function discountedPoints(baseCost: number, discount: number): number {
  const baseHundredths = Math.max(0, Math.round(baseCost * 100))
  const rateHundredths = Math.round(Math.min(1, Math.max(0, discount)) * 100)
  return Math.ceil((baseHundredths * rateHundredths) / 10_000)
}

function auditSuccessText(action: PermissionAuditAction): string {
  switch (action) {
    case 'APPROVE':
      return '已通过该权限申请'
    case 'REJECT':
      return '已驳回该权限申请'
    case 'REVOKE':
      return '已收回该接口权限'
  }
}

function statusMeta(status: PermissionStatus): { label: string; className: string } {
  switch (status) {
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

function statusTagType(status: PermissionStatus): TagType {
  switch (status) {
    case 0:
      return 'warning'
    case 1:
      return 'success'
    case 2:
      return 'danger'
    case 3:
      return 'info'
  }
}

function methodClass(method: string): string {
  return `method-${method.toLowerCase()}`
}

function formatTime(value: string | null): string {
  if (!value) {
    return '—'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return date.toLocaleString('zh-CN', { hour12: false })
}
</script>

<template>
  <div class="admin-api-page">
    <div class="page-head">
      <div>
        <div class="title-row">
          <span class="title-icon"><el-icon :size="20"><Stamp /></el-icon></span>
          <h2>API管理</h2>
        </div>
        <p>
          在「API权限审批工单」中审批开发者提交的调用权限申请，通过时可设置 0.00 - 1.00 的专属折扣；
          在「API发布管理」中变更接口上下线状态（新增接口默认下线，上线后才可被调用）；
          在「API定价管理」中可直接调整每个接口的基础点数，变更立即生效。
        </p>
      </div>
      <el-button :icon="Refresh" :loading="refreshLoading" @click="refreshActive">
        刷新
      </el-button>
    </div>

    <el-card shadow="never" class="panel-card">
      <el-tabs v-model="activeTab">
        <el-tab-pane name="tickets">
          <template #label>
            <span class="tab-label"><el-icon><EditPen /></el-icon>API权限审批工单</span>
          </template>

          <div class="filter-bar">
            <el-select v-model="statusFilter" class="status-select" placeholder="全部状态">
              <el-option label="全部状态" value="" />
              <el-option label="待审批" value="0" />
              <el-option label="已通过" value="1" />
              <el-option label="已驳回" value="2" />
              <el-option label="已收回" value="3" />
            </el-select>
            <el-input
              v-model="keyword"
              :prefix-icon="Search"
              clearable
              placeholder="搜索用户名或接口名"
              class="keyword-input"
              @keyup.enter="applyFilters"
              @clear="applyFilters"
            />
            <el-button type="primary" :icon="Search" @click="applyFilters">查询</el-button>
            <el-button @click="resetFilters">重置</el-button>
          </div>

          <el-table
            v-loading="loading"
            :data="tickets"
            stripe
            row-key="id"
            empty-text="没有匹配的申请工单"
          >
            <el-table-column label="申请人" min-width="160">
              <template #default="{ row }">
                <div class="applicant-cell">
                  <span class="applicant-avatar">
                    {{ (row.username || '?').slice(0, 1).toUpperCase() }}
                  </span>
                  <div class="cell-copy">
                    <p class="primary-text">{{ row.username || '已注销用户' }}</p>
                    <p class="sub-text">ID #{{ row.userId }}</p>
                  </div>
                </div>
              </template>
            </el-table-column>

            <el-table-column label="接口" min-width="230">
              <template #default="{ row }">
                <div class="api-cell">
                  <span :class="['method-tag', methodClass(row.endpointMethod || '')]">
                    {{ row.endpointMethod || '—' }}
                  </span>
                  <div class="cell-copy">
                    <p class="primary-text">
                      {{ row.endpointName || `接口 #${row.endpointId}` }}
                    </p>
                    <code class="sub-text">{{ row.endpointPath || '—' }}</code>
                  </div>
                </div>
              </template>
            </el-table-column>

            <el-table-column label="基础点数" width="100">
              <template #default="{ row }">
                <span class="points-text">{{ row.baseCost }} 点</span>
              </template>
            </el-table-column>

            <el-table-column label="折扣 / 实际" width="120">
              <template #default="{ row }">
                <div v-if="row.status === 1" class="discount-cell">
                  <span>{{ row.customDiscount.toFixed(2) }}</span>
                  <small>{{ row.actualCost }} 点 / 次</small>
                </div>
                <span v-else class="sub-text">—</span>
              </template>
            </el-table-column>

            <el-table-column label="申请理由" min-width="220">
              <template #default="{ row }">
                <p class="reason-text">{{ row.applyReason }}</p>
              </template>
            </el-table-column>

            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.status)" effect="plain" size="small" round>
                  {{ statusMeta(row.status).label }}
                </el-tag>
              </template>
            </el-table-column>

            <el-table-column label="申请时间" width="160">
              <template #default="{ row }">
                <span class="sub-text">{{ formatTime(row.createTime) }}</span>
              </template>
            </el-table-column>

            <el-table-column label="操作" width="110" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="row.status === 0"
                  link
                  type="primary"
                  :icon="EditPen"
                  @click="openAudit(row)"
                >
                  审批
                </el-button>
                <el-button
                  v-else-if="row.status === 1"
                  link
                  type="danger"
                  :icon="Lock"
                  @click="openAudit(row)"
                >
                  收回
                </el-button>
                <span v-else class="sub-text">已结束</span>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination-row">
            <el-pagination
              background
              layout="total, sizes, prev, pager, next"
              :total="total"
              :page-size="size"
              :current-page="page"
              :page-sizes="[10, 20, 50, 100]"
              @current-change="handlePageChange"
              @size-change="handleSizeChange"
            />
          </div>
        </el-tab-pane>

        <el-tab-pane name="publish">
          <template #label>
            <span class="tab-label"><el-icon><Promotion /></el-icon>API发布管理</span>
          </template>

          <el-alert type="info" :closable="false" show-icon class="pricing-alert">
            新增接口默认处于下线状态，需在此上线（状态改为 1）后才可被调用；下线后的接口对调用方统一返回「API 已下线」。
          </el-alert>

          <div class="filter-bar">
            <el-select v-model="publishStatusFilter" class="status-select" placeholder="全部状态">
              <el-option label="全部状态" value="" />
              <el-option label="已上线" value="1" />
              <el-option label="已下线" value="0" />
            </el-select>
            <el-input
              v-model="publishKeyword"
              :prefix-icon="Search"
              clearable
              placeholder="搜索接口名称或路径"
              class="keyword-input"
              @keyup.enter="applyPublishFilters"
              @clear="applyPublishFilters"
            />
            <el-button type="primary" :icon="Search" @click="applyPublishFilters">查询</el-button>
            <el-button @click="resetPublishFilters">重置</el-button>
          </div>

          <el-table
            v-loading="publishLoading"
            :data="publishRows"
            stripe
            row-key="id"
            empty-text="暂无接口目录"
          >
            <el-table-column label="接口" min-width="300">
              <template #default="{ row }">
                <div class="api-cell">
                  <span :class="['method-tag', methodClass(row.method)]">{{ row.method }}</span>
                  <div class="cell-copy">
                    <p class="primary-text">{{ row.name }}</p>
                    <code class="sub-text">{{ row.path }}</code>
                  </div>
                </div>
              </template>
            </el-table-column>

            <el-table-column label="引擎类型" width="130">
              <template #default="{ row }">
                <el-tag size="small" effect="plain" round>{{ row.engineType || '—' }}</el-tag>
              </template>
            </el-table-column>

            <el-table-column label="基础点数" width="110">
              <template #default="{ row }">
                <span class="points-text">{{ row.baseCost }} 点 / 次</span>
              </template>
            </el-table-column>

            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag
                  :type="row.endpointStatus === 1 ? 'success' : 'info'"
                  effect="plain"
                  size="small"
                  round
                >
                  {{ row.endpointStatus === 1 ? '已上线' : '已下线' }}
                </el-tag>
              </template>
            </el-table-column>

            <el-table-column label="更新时间" width="170">
              <template #default="{ row }">
                <span class="sub-text">{{ formatTime(row.updateTime) }}</span>
              </template>
            </el-table-column>

            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="row.endpointStatus === 1"
                  link
                  type="danger"
                  :icon="CircleClose"
                  :loading="togglingId === row.id"
                  @click="togglePublish(row)"
                >
                  下线
                </el-button>
                <el-button
                  v-else
                  link
                  type="primary"
                  :icon="CircleCheck"
                  :loading="togglingId === row.id"
                  @click="togglePublish(row)"
                >
                  上线
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane name="pricing">
          <template #label>
            <span class="tab-label"><el-icon><PriceTag /></el-icon>API 定价管理</span>
          </template>

          <el-alert type="info" :closable="false" show-icon class="pricing-alert">
            基础点数调整后立即生效，所有后续调用按新点数计费；已设置专属折扣的用户仍在此基础上计算折后点数。
          </el-alert>

          <el-table
            v-loading="pricingLoading"
            :data="pricingRows"
            stripe
            row-key="id"
            empty-text="暂无接口目录"
          >
            <el-table-column label="接口" min-width="280">
              <template #default="{ row }">
                <div class="api-cell">
                  <span :class="['method-tag', methodClass(row.method)]">{{ row.method }}</span>
                  <div class="cell-copy">
                    <p class="primary-text">{{ row.name }}</p>
                    <code class="sub-text">{{ row.path }}</code>
                  </div>
                </div>
              </template>
            </el-table-column>

            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag
                  :type="row.endpointStatus === 1 ? 'success' : 'info'"
                  effect="plain"
                  size="small"
                  round
                >
                  {{ row.endpointStatus === 1 ? '已上线' : '已下线' }}
                </el-tag>
              </template>
            </el-table-column>

            <el-table-column label="当前基础点数" width="130">
              <template #default="{ row }">
                <span class="points-text">{{ row.baseCost }} 点 / 次</span>
              </template>
            </el-table-column>

            <el-table-column label="新基础点数" width="180">
              <template #default="{ row }">
                <el-input-number
                  v-model="row.draftCost"
                  :min="0"
                  :max="1000000"
                  :step="1"
                  :precision="0"
                  controls-position="right"
                  class="cost-input"
                />
              </template>
            </el-table-column>

            <el-table-column label="操作" width="110" fixed="right">
              <template #default="{ row }">
                <el-button
                  type="primary"
                  link
                  :loading="savingId === row.id"
                  :disabled="row.draftCost === row.baseCost"
                  @click="savePrice(row)"
                >
                  保存
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog
      v-model="auditVisible"
      title="审批权限申请"
      width="580px"
      :close-on-click-modal="false"
    >
      <template v-if="auditTarget">
        <div class="audit-summary">
          <div class="summary-head">
            <div>
              <p class="primary-text">
                {{ auditTarget.endpointName || `接口 #${auditTarget.endpointId}` }}
              </p>
              <code class="sub-text">{{ auditTarget.endpointPath || '—' }}</code>
            </div>
            <el-tag
              :type="statusTagType(auditTarget.status)"
              effect="plain"
              size="small"
              round
            >
              {{ statusMeta(auditTarget.status).label }}
            </el-tag>
          </div>

          <div class="summary-grid">
            <div>
              <span class="summary-label">申请人</span>
              <strong>{{ auditTarget.username || '已注销用户' }}</strong>
            </div>
            <div>
              <span class="summary-label">基础点数</span>
              <strong>{{ auditTarget.baseCost }} 点 / 次</strong>
            </div>
          </div>

          <div class="summary-reason">
            <span class="summary-label">申请理由</span>
            <p>{{ auditTarget.applyReason }}</p>
          </div>

          <div v-if="auditTarget.auditRemark" class="summary-reason">
            <span class="summary-label">历史审批意见</span>
            <p>{{ auditTarget.auditRemark }}</p>
          </div>
        </div>

        <el-form label-position="top" class="mt-4">
          <el-form-item label="审批动作">
            <el-radio-group v-model="auditForm.action">
              <el-radio-button
                v-for="action in availableActions"
                :key="action.value"
                :value="action.value"
              >
                {{ action.label }}
              </el-radio-button>
            </el-radio-group>
          </el-form-item>

          <template v-if="auditForm.action === 'APPROVE'">
            <el-form-item label="专属折扣率（0.00 - 1.00）">
              <div class="discount-editor">
                <el-input-number
                  :model-value="auditForm.customDiscount"
                  :min="0"
                  :max="1"
                  :step="0.05"
                  :precision="2"
                  controls-position="right"
                  class="discount-input"
                  @update:model-value="setDiscount"
                />
                <div class="preset-row">
                  <el-button
                    v-for="preset in DISCOUNT_PRESETS"
                    :key="preset.value"
                    size="small"
                    :type="auditForm.customDiscount === preset.value ? 'primary' : 'default'"
                    @click="auditForm.customDiscount = preset.value"
                  >
                    {{ preset.label }}
                  </el-button>
                </div>
              </div>
            </el-form-item>

            <el-alert type="info" :closable="false" show-icon class="cost-preview">
              基础点数 {{ auditTarget.baseCost }} 点 ×
              {{ auditForm.customDiscount.toFixed(2) }}，折后每次扣
              <strong>{{ auditActualCost }}</strong> 点
              <span v-if="auditActualCost === 0">（免费调用）</span>
            </el-alert>
          </template>

          <el-form-item
            :label="auditForm.action === 'REJECT' ? '驳回原因（必填）' : '审批意见'"
          >
            <el-input
              v-model="auditForm.auditRemark"
              type="textarea"
              :rows="4"
              maxlength="512"
              show-word-limit
              :placeholder="
                auditForm.action === 'APPROVE'
                  ? '可填写开通说明或使用限制'
                  : auditForm.action === 'REJECT'
                    ? '请说明驳回原因，便于申请人调整后重新提交'
                    : '可填写收回原因'
              "
            />
          </el-form-item>
        </el-form>
      </template>

      <template #footer>
        <el-button @click="auditVisible = false">取消</el-button>
        <el-button type="primary" :loading="auditing" @click="submitAudit">
          {{ auditSubmitLabel }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.admin-api-page {
  display: grid;
  gap: 1.25rem;
}

.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1.5rem;
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
  max-width: 56rem;
  margin: 0.45rem 0 0;
  font-size: 0.85rem;
  line-height: 1.7;
  color: #64748b;
}

.panel-card {
  border: 1px solid #e2e8f0;
  border-radius: 14px;
}

.tab-label {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  font-weight: 600;
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 1rem;
}

.status-select {
  width: 150px;
}

.keyword-input {
  flex: 1 1 260px;
  max-width: 340px;
}

:deep(.el-table) {
  --el-table-header-bg-color: #f8fafc;
  --el-table-border-color: #eef2f7;
}

.applicant-cell,
.api-cell {
  display: flex;
  align-items: center;
  gap: 0.7rem;
}

.applicant-avatar {
  display: grid;
  flex-shrink: 0;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  background: #eef2ff;
  color: #4338ca;
  font-size: 0.82rem;
  font-weight: 700;
}

.cell-copy {
  min-width: 0;
}

.primary-text {
  margin: 0;
  overflow: hidden;
  font-size: 0.85rem;
  font-weight: 600;
  color: #0f172a;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sub-text {
  margin: 0.18rem 0 0;
  overflow: hidden;
  font-size: 0.72rem;
  color: #94a3b8;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.method-tag {
  display: inline-flex;
  flex-shrink: 0;
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

.points-text {
  font-size: 0.82rem;
  font-weight: 600;
  color: #0f172a;
}

.discount-cell {
  display: grid;
  gap: 0.1rem;
}

.discount-cell span {
  font-size: 0.82rem;
  font-weight: 600;
  color: #4338ca;
}

.discount-cell small {
  font-size: 0.68rem;
  color: #94a3b8;
}

.reason-text {
  display: -webkit-box;
  margin: 0;
  overflow: hidden;
  font-size: 0.78rem;
  line-height: 1.55;
  color: #475569;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
  padding-top: 1rem;
}

.pricing-alert {
  margin-bottom: 1rem;
}

.cost-input {
  width: 150px;
}

.audit-summary {
  padding: 0.95rem 1rem;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #f8fafc;
}

.summary-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.75rem;
  margin-top: 0.9rem;
  padding-top: 0.9rem;
  border-top: 1px dashed #e2e8f0;
}

.summary-grid > div {
  display: grid;
  gap: 0.2rem;
}

.summary-label {
  font-size: 0.68rem;
  color: #94a3b8;
}

.summary-grid strong {
  font-size: 0.82rem;
  color: #0f172a;
}

.summary-reason {
  margin-top: 0.85rem;
}

.summary-reason p {
  margin: 0.25rem 0 0;
  font-size: 0.78rem;
  line-height: 1.6;
  color: #475569;
  white-space: pre-wrap;
}

.discount-editor {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.7rem;
}

.discount-input {
  width: 140px;
}

.preset-row {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
}

.cost-preview {
  margin-bottom: 1.1rem;
}

.cost-preview strong {
  color: #4338ca;
}

@media (max-width: 720px) {
  .page-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .status-select,
  .keyword-input {
    width: 100%;
    max-width: none;
  }
}
</style>

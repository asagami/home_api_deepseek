<script setup lang="ts">
import { nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { CopyDocument, Key, Plus, Refresh, WarningFilled } from '@element-plus/icons-vue'
import {
  createApiKey,
  deleteApiKey,
  listApiKeys,
  updateApiKeyStatus,
  type ApiKeyCreateResult,
  type ApiKeyItem,
  type ApiKeyStatus,
} from '@/api/apikeys'
import { copyText } from '@/utils/clipboard'
import { toErrorMessage } from '@/utils/error'
import { useAuthStore } from '@/stores/auth'

defineOptions({ name: 'ApiKeysView' })

const auth = useAuthStore()

const items = ref<ApiKeyItem[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)
const statusUpdatingId = ref<string | null>(null)

const createVisible = ref(false)
const creating = ref(false)
const createFormRef = ref<FormInstance>()
const createForm = reactive({
  keyName: '',
  rateLimitQps: 5,
  expiredTime: null as string | null,
})

const createRules: FormRules = {
  keyName: [
    { required: true, message: '请输入 Key 名称', trigger: 'blur' },
    { max: 64, message: '名称长度不能超过 64', trigger: 'blur' },
  ],
  rateLimitQps: [{ required: true, message: '请设置 QPS', trigger: 'change' }],
}

const createdKey = ref<ApiKeyCreateResult | null>(null)
const createdVisible = ref(false)
const copied = ref(false)

onMounted(() => {
  void loadKeys()
})

async function loadKeys(): Promise<void> {
  loading.value = true
  try {
    const result = await listApiKeys({ page: page.value, size: size.value })
    items.value = result.records
    total.value = result.total
    page.value = result.pageNum
  } catch (error) {
    ElMessage.error(toErrorMessage(error))
  } finally {
    loading.value = false
  }
}

function handlePageChange(value: number): void {
  page.value = value
  void loadKeys()
}

async function openCreate(): Promise<void> {
  createForm.keyName = ''
  createForm.rateLimitQps = 5
  createForm.expiredTime = null
  createVisible.value = true
  await nextTick()
  createFormRef.value?.clearValidate()
}

async function submitCreate(): Promise<void> {
  if (!createFormRef.value) {
    return
  }
  const valid = await createFormRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }
  if (createForm.expiredTime && new Date(createForm.expiredTime).getTime() <= Date.now()) {
    ElMessage.warning('过期时间必须晚于当前时间')
    return
  }

  creating.value = true
  try {
    const result = await createApiKey({
      keyName: createForm.keyName.trim(),
      rateLimitQps: createForm.rateLimitQps,
      expiredTime: createForm.expiredTime,
    })
    createVisible.value = false
    createdKey.value = result
    copied.value = false
    createdVisible.value = true
    await loadKeys()
    await auth.fetchMe().catch(() => null)
  } catch (error) {
    ElMessage.error(toErrorMessage(error))
  } finally {
    creating.value = false
  }
}

async function copyCreatedKey(): Promise<void> {
  if (!createdKey.value) {
    return
  }
  try {
    await copyText(createdKey.value.apiKey)
    copied.value = true
    ElMessage.success('API Key 已复制到剪贴板')
  } catch (error) {
    ElMessage.error(toErrorMessage(error))
  }
}

function resetCreated(): void {
  createdKey.value = null
  copied.value = false
}

function handleStatusChange(row: ApiKeyItem, value: string | number | boolean): void {
  const nextStatus: ApiKeyStatus = value ? 1 : 0
  statusUpdatingId.value = row.id
  void updateApiKeyStatus(row.id, nextStatus)
    .then(() => {
      row.status = nextStatus
      ElMessage.success(nextStatus === 1 ? '已启用该 Key' : '已禁用该 Key')
    })
    .catch(async (error: unknown) => {
      ElMessage.error(toErrorMessage(error))
      await loadKeys()
    })
    .finally(() => {
      statusUpdatingId.value = null
    })
}

async function handleDelete(row: ApiKeyItem): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `删除后「${row.keyName}」将立即失效且无法恢复，历史调用日志不受影响。`,
      '删除 API Key',
      {
        type: 'warning',
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
        confirmButtonClass: 'el-button--danger',
      },
    )
  } catch {
    return
  }

  try {
    await deleteApiKey(row.id)
    ElMessage.success('API Key 已删除')
    if (items.value.length === 1 && page.value > 1) {
      page.value -= 1
    }
    await loadKeys()
    await auth.fetchMe().catch(() => null)
  } catch (error) {
    ElMessage.error(toErrorMessage(error))
  }
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

function disablePastDate(date: Date): boolean {
  return date.getTime() < Date.now() - 24 * 60 * 60 * 1000
}
</script>

<template>
  <div class="apikeys-page">
    <div class="page-head">
      <div>
        <h2>API Keys</h2>
        <p>
          每个 Key 使用 SHA-256 哈希存储，明文仅在创建成功时展示一次。调用时通过
          <code>X-API-Key</code> 请求头传递。
        </p>
      </div>
      <div class="head-actions">
        <el-button :icon="Refresh" :loading="loading" @click="loadKeys">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">创建 API Key</el-button>
      </div>
    </div>

    <el-card shadow="never" class="table-card">
      <el-table
        v-loading="loading"
        :data="items"
        stripe
        class="keys-table"
        empty-text="还没有 API Key"
      >
        <el-table-column label="名称 / 前缀" min-width="190">
          <template #default="{ row }">
            <div class="key-name">{{ row.keyName }}</div>
            <code class="key-prefix">{{ row.keyPrefix }}</code>
          </template>
        </el-table-column>

        <el-table-column label="QPS" width="90">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.rateLimitQps }} QPS</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="已用额度" width="130">
          <template #default="{ row }">
            <div class="quota-cell">
              <strong>{{ row.usedQuota }} 次</strong>
              <small>
                {{ row.totalQuota === -1 ? '共享账户额度' : `总额度 ${row.totalQuota}` }}
              </small>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <div class="status-cell">
              <el-switch
                :model-value="row.status === 1"
                :loading="statusUpdatingId === row.id"
                @change="(value: string | number | boolean) => handleStatusChange(row, value)"
              />
              <span :class="['status-text', row.status === 1 ? 'is-enabled' : 'is-disabled']">
                {{ row.status === 1 ? '启用' : '禁用' }}
              </span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="最后调用" width="140">
          <template #default="{ row }">
            <span class="time-text">{{ formatTime(row.lastUsedTime) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="创建时间" width="140">
          <template #default="{ row }">
            <span class="time-text">{{ formatTime(row.createTime) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="70">
          <template #default="{ row }">
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="还没有 API Key，创建一个开始调用接口">
            <el-button type="primary" :icon="Plus" @click="openCreate">创建第一个 Key</el-button>
          </el-empty>
        </template>
      </el-table>

      <div class="pagination-row">
        <el-pagination
          background
          layout="total, prev, pager, next"
          :total="total"
          :page-size="size"
          :current-page="page"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>

    <el-dialog
      v-model="createVisible"
      title="创建 API Key"
      width="480px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="createFormRef"
        :model="createForm"
        :rules="createRules"
        label-position="top"
      >
        <el-form-item label="Key 名称" prop="keyName">
          <el-input
            v-model="createForm.keyName"
            maxlength="64"
            show-word-limit
            placeholder="例如：生产环境 Key"
          />
        </el-form-item>
        <el-form-item label="QPS 限制" prop="rateLimitQps">
          <el-input-number v-model="createForm.rateLimitQps" :min="1" :max="10000" />
          <span class="field-hint">每个 Key 独立限流，默认 5 QPS</span>
        </el-form-item>
        <el-form-item label="过期时间（选填）">
          <el-date-picker
            v-model="createForm.expiredTime"
            type="datetime"
            placeholder="不选择表示永不过期"
            value-format="YYYY-MM-DDTHH:mm:ss"
            :disabled-date="disablePastDate"
            class="date-picker"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">
          创建密钥
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="createdVisible"
      width="600px"
      class="created-dialog"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      :show-close="false"
      @closed="resetCreated"
    >
      <template #header>
        <div class="created-header">
          <div class="created-icon">
            <el-icon :size="22"><Key /></el-icon>
          </div>
          <div>
            <p class="created-title">API Key 创建成功</p>
            <p class="created-sub">请立即复制并妥善保存</p>
          </div>
        </div>
      </template>

      <el-alert
        type="warning"
        :closable="false"
        show-icon
        class="one-time-alert"
        title="这是唯一一次展示明文 Key 的机会"
      >
        <template #default>
          关闭弹窗后将无法再次查看完整 Key，数据库只保存 SHA-256 哈希与脱敏前缀。
          如果丢失，只能删除后重新创建。
        </template>
      </el-alert>

      <div class="key-display">
        <el-input
          :model-value="createdKey?.apiKey ?? ''"
          readonly
          type="textarea"
          :autosize="{ minRows: 2, maxRows: 3 }"
          class="key-input"
        />
        <el-button type="primary" :icon="CopyDocument" class="copy-button" @click="copyCreatedKey">
          {{ copied ? '已复制' : '一键复制' }}
        </el-button>
      </div>

      <div class="key-meta">
        <span><el-icon><WarningFilled /></el-icon> 前缀：<code>{{ createdKey?.keyPrefix }}</code></span>
        <span>QPS：{{ createdKey?.rateLimitQps }}</span>
      </div>

      <template #footer>
        <el-button type="primary" @click="createdVisible = false">我已安全保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.apikeys-page {
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
  max-width: 46rem;
  margin: 0.45rem 0 0;
  font-size: 0.85rem;
  line-height: 1.7;
  color: #64748b;
}

.page-head code {
  padding: 0.12rem 0.35rem;
  border-radius: 5px;
  background: #eef2ff;
  color: #4338ca;
  font-size: 0.78rem;
}

.head-actions {
  display: flex;
  gap: 0.6rem;
  flex-shrink: 0;
}

.table-card {
  border: 1px solid #e2e8f0;
  border-radius: 14px;
}

.keys-table {
  --el-table-header-bg-color: #f8fafc;
  --el-table-border-color: #eef2f7;
}

.key-name {
  margin-bottom: 0.25rem;
  font-size: 0.88rem;
  font-weight: 600;
  color: #0f172a;
}

.key-prefix {
  padding: 0.14rem 0.4rem;
  border-radius: 5px;
  background: #f1f5f9;
  color: #475569;
  font-size: 0.74rem;
}

.quota-cell {
  display: grid;
  gap: 0.12rem;
}

.quota-cell strong {
  font-size: 0.8rem;
  font-weight: 600;
  color: #334155;
}

.quota-cell small {
  font-size: 0.68rem;
  color: #94a3b8;
}

.status-cell {
  display: flex;
  align-items: center;
  gap: 0.45rem;
}

.status-text {
  font-size: 0.78rem;
}

.status-text.is-enabled {
  color: #059669;
}

.status-text.is-disabled {
  color: #94a3b8;
}

.time-text {
  font-size: 0.78rem;
  color: #64748b;
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
  padding-top: 1rem;
}

.field-hint {
  margin-left: 0.75rem;
  font-size: 0.74rem;
  color: #94a3b8;
}

.date-picker {
  width: 100%;
}

.created-header {
  display: flex;
  align-items: center;
  gap: 0.8rem;
}

.created-icon {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: #eef2ff;
  color: #4f46e5;
}

.created-title {
  margin: 0;
  font-size: 1.1rem;
  font-weight: 700;
  color: #0f172a;
}

.created-sub {
  margin: 0.15rem 0 0;
  font-size: 0.78rem;
  color: #64748b;
}

.one-time-alert {
  margin-bottom: 1.1rem;
}

.key-display {
  display: grid;
  gap: 0.65rem;
}

.key-input :deep(.el-textarea__inner) {
  font-family: 'JetBrains Mono', 'SFMono-Regular', Menlo, Consolas, monospace;
  font-size: 0.8rem;
  line-height: 1.6;
  color: #0f172a;
  background: #f8fafc;
}

.copy-button {
  justify-self: start;
}

.key-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 1rem;
  margin-top: 0.9rem;
  font-size: 0.76rem;
  color: #64748b;
}

.key-meta span {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
}

.key-meta code {
  padding: 0.1rem 0.3rem;
  border-radius: 4px;
  background: #f1f5f9;
  font-size: 0.74rem;
}

@media (max-width: 720px) {
  .page-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .head-actions {
    width: 100%;
  }
}
</style>

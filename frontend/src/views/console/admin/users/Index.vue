<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Coin, Refresh, Search } from '@element-plus/icons-vue'
import {
  listAdminUsers,
  rechargeUserQuota,
  updateUserStatus,
  type AdminUserItem,
  type UserStatus,
} from '@/api/adminUsers'
import { toErrorMessage } from '@/utils/error'
import { useAuthStore } from '@/stores/auth'

defineOptions({ name: 'AdminUsersView' })

const QUICK_AMOUNTS = [100, 500, 1000, 5000]

const auth = useAuthStore()

const users = ref<AdminUserItem[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)
const statusUpdatingId = ref<string | null>(null)

const keyword = ref('')
const statusFilter = ref<'' | '0' | '1'>('')

const rechargeVisible = ref(false)
const rechargeTarget = ref<AdminUserItem | null>(null)
const rechargeAmount = ref(1000)
const recharging = ref(false)

const previewQuota = computed(
  () => (rechargeTarget.value?.balanceQuota ?? 0) + (rechargeAmount.value || 0),
)

onMounted(() => {
  void loadUsers()
})

async function loadUsers(): Promise<void> {
  loading.value = true
  try {
    const result = await listAdminUsers({
      page: page.value,
      size: size.value,
      keyword: keyword.value.trim() || undefined,
      status: statusFilter.value === '' ? undefined : Number(statusFilter.value),
    })
    users.value = result.records
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
  void loadUsers()
}

function resetFilters(): void {
  keyword.value = ''
  statusFilter.value = ''
  page.value = 1
  void loadUsers()
}

function handlePageChange(value: number): void {
  page.value = value
  void loadUsers()
}

function handleSizeChange(value: number): void {
  size.value = value
  page.value = 1
  void loadUsers()
}

async function handleStatusChange(
  row: AdminUserItem,
  value: string | number | boolean,
): Promise<void> {
  const nextStatus: UserStatus = value ? 1 : 0
  if (nextStatus === 0) {
    try {
      await ElMessageBox.confirm(
        `封禁后「${row.username}」将无法登录控制台，且其 API Key 立即失效。`,
        '封禁用户',
        {
          type: 'warning',
          confirmButtonText: '确认封禁',
          cancelButtonText: '取消',
          confirmButtonClass: 'el-button--danger',
        },
      )
    } catch {
      return
    }
  }

  statusUpdatingId.value = row.id
  try {
    await updateUserStatus(row.id, nextStatus)
    row.status = nextStatus
    ElMessage.success(nextStatus === 1 ? `已解封 ${row.username}` : `已封禁 ${row.username}`)
    if (auth.user?.id === row.id) {
      await auth.fetchMe().catch(() => null)
    }
  } catch (error) {
    ElMessage.error(toErrorMessage(error))
  } finally {
    statusUpdatingId.value = null
  }
}

function openRecharge(row: AdminUserItem): void {
  rechargeTarget.value = row
  rechargeAmount.value = 1000
  rechargeVisible.value = true
}

async function submitRecharge(): Promise<void> {
  const target = rechargeTarget.value
  if (!target) {
    return
  }
  if (!Number.isFinite(rechargeAmount.value) || rechargeAmount.value <= 0) {
    ElMessage.warning('充值额度必须大于 0')
    return
  }

  recharging.value = true
  try {
    const amount = rechargeAmount.value
    const updated = await rechargeUserQuota(target.id, amount)
    const index = users.value.findIndex((item) => item.id === updated.id)
    if (index >= 0) {
      users.value[index] = updated
    }
    rechargeVisible.value = false
    ElMessage.success(`已为 ${updated.username} 充值 ${amount.toLocaleString('zh-CN')} 次`)
    if (auth.user?.id === updated.id) {
      await auth.fetchMe().catch(() => null)
    }
  } catch (error) {
    ElMessage.error(toErrorMessage(error))
  } finally {
    recharging.value = false
  }
}

function formatNumber(value: number | null | undefined): string {
  return (value ?? 0).toLocaleString('zh-CN')
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
  <div class="admin-users-page">
    <div class="page-head">
      <div>
        <h2>用户管理</h2>
        <p>
          管理开发者账号的访问状态与调用额度。封禁会立即阻止该用户登录控制台，
          其名下 API Key 也会在下次调用时被拒绝；额度充值为原子追加，不会覆盖已有余额。
        </p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="loadUsers">刷新</el-button>
    </div>

    <el-card shadow="never" class="table-card">
      <div class="filter-bar">
        <el-input
          v-model="keyword"
          :prefix-icon="Search"
          clearable
          placeholder="搜索用户名或邮箱"
          class="keyword-input"
          @keyup.enter="applyFilters"
          @clear="applyFilters"
        />
        <el-select v-model="statusFilter" placeholder="全部状态" class="status-select">
          <el-option label="全部状态" value="" />
          <el-option label="正常" value="1" />
          <el-option label="已封禁" value="0" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="applyFilters">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="users"
        stripe
        class="users-table"
        empty-text="没有匹配的用户"
      >
        <el-table-column label="用户" min-width="220">
          <template #default="{ row }">
            <div class="user-cell">
              <div class="user-avatar">{{ row.username.slice(0, 1).toUpperCase() }}</div>
              <div class="user-meta">
                <p class="user-name">{{ row.username }}</p>
                <p class="user-sub">{{ row.email ?? '未填写邮箱' }} · ID #{{ row.id }}</p>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="角色" width="100">
          <template #default="{ row }">
            <el-tag
              :type="row.role === 'ADMIN' ? 'warning' : 'info'"
              effect="plain"
              size="small"
              round
            >
              {{ row.role }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="剩余额度" width="130">
          <template #default="{ row }">
            <div class="quota-cell">
              <strong>{{ formatNumber(row.balanceQuota) }}</strong>
              <small>次</small>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="140">
          <template #default="{ row }">
            <div class="status-cell">
              <el-switch
                :model-value="row.status === 1"
                :loading="statusUpdatingId === row.id"
                :disabled="row.id === auth.user?.id"
                :title="row.id === auth.user?.id ? '不能封禁当前登录账号' : undefined"
                @change="(value: string | number | boolean) => handleStatusChange(row, value)"
              />
              <span :class="['status-text', row.status === 1 ? 'is-enabled' : 'is-banned']">
                {{ row.status === 1 ? '正常' : '已封禁' }}
              </span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="注册时间" width="160">
          <template #default="{ row }">
            <span class="time-text">{{ formatTime(row.createTime) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Coin" @click="openRecharge(row)">
              充值额度
            </el-button>
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
    </el-card>

    <el-dialog
      v-model="rechargeVisible"
      title="额度充值"
      width="460px"
      :close-on-click-modal="false"
    >
      <template v-if="rechargeTarget">
        <div class="target-card">
          <div class="user-avatar">{{ rechargeTarget.username.slice(0, 1).toUpperCase() }}</div>
          <div>
            <p class="target-name">{{ rechargeTarget.username }}</p>
            <p class="target-sub">
              当前剩余 <strong>{{ formatNumber(rechargeTarget.balanceQuota) }}</strong> 次
            </p>
          </div>
        </div>

        <div class="amount-field">
          <label class="field-label">追加额度</label>
          <el-input-number
            v-model="rechargeAmount"
            :min="1"
            :max="10000000"
            :step="100"
            controls-position="right"
            class="amount-input"
          />
          <span class="field-hint">单次最多 1000 万次</span>
        </div>

        <el-radio-group v-model="rechargeAmount" class="quick-amounts">
          <el-radio-button v-for="amount in QUICK_AMOUNTS" :key="amount" :value="amount">
            +{{ amount.toLocaleString('zh-CN') }}
          </el-radio-button>
        </el-radio-group>

        <el-alert type="info" :closable="false" show-icon class="preview-alert">
          充值后剩余额度：{{ formatNumber(previewQuota) }} 次
        </el-alert>
      </template>

      <template #footer>
        <el-button @click="rechargeVisible = false">取消</el-button>
        <el-button type="primary" :loading="recharging" @click="submitRecharge">
          确认充值
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.admin-users-page {
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
  max-width: 52rem;
  margin: 0.45rem 0 0;
  font-size: 0.85rem;
  line-height: 1.7;
  color: #64748b;
}

.table-card {
  border: 1px solid #e2e8f0;
  border-radius: 14px;
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 1rem;
}

.keyword-input {
  flex: 1 1 260px;
  max-width: 340px;
}

.status-select {
  width: 150px;
}

.users-table {
  --el-table-header-bg-color: #f8fafc;
  --el-table-border-color: #eef2f7;
}

.user-cell {
  display: flex;
  align-items: center;
  gap: 0.7rem;
}

.user-avatar {
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

.user-meta {
  min-width: 0;
}

.user-name {
  margin: 0;
  font-size: 0.86rem;
  font-weight: 600;
  color: #0f172a;
}

.user-sub {
  margin: 0.18rem 0 0;
  overflow: hidden;
  font-size: 0.7rem;
  color: #94a3b8;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.quota-cell {
  display: flex;
  align-items: baseline;
  gap: 0.25rem;
}

.quota-cell strong {
  font-size: 0.88rem;
  color: #0f172a;
}

.quota-cell small {
  font-size: 0.7rem;
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

.status-text.is-banned {
  color: #dc2626;
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

.target-card {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.85rem;
  margin-bottom: 1.1rem;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #f8fafc;
}

.target-name {
  margin: 0;
  font-size: 0.9rem;
  font-weight: 600;
  color: #0f172a;
}

.target-sub {
  margin: 0.2rem 0 0;
  font-size: 0.74rem;
  color: #64748b;
}

.target-sub strong {
  color: #4338ca;
}

.amount-field {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.6rem;
}

.field-label {
  font-size: 0.82rem;
  font-weight: 600;
  color: #334155;
}

.amount-input {
  width: 180px;
}

.field-hint {
  font-size: 0.7rem;
  color: #94a3b8;
}

.quick-amounts {
  margin-top: 0.9rem;
}

.preview-alert {
  margin-top: 1.1rem;
}

@media (max-width: 720px) {
  .page-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .keyword-input,
  .status-select {
    width: 100%;
    max-width: none;
  }
}
</style>

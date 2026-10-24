<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import { getLogStats, listLogs, type LogItem, type LogStats } from '@/api/logs'
import { toErrorMessage } from '@/utils/error'

defineOptions({ name: 'LogsView' })

type StatusTagType = 'success' | 'warning' | 'danger' | 'info'

const STATUS_OPTIONS = [200, 201, 204, 400, 401, 403, 404, 429, 500, 502, 503]

const items = ref<LogItem[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)

const stats = ref<LogStats | null>(null)
const statsLoading = ref(false)

const filters = reactive<{
  range: [string, string] | null
  httpStatus: number | undefined
}>({
  range: defaultRange(),
  httpStatus: undefined,
})

const maxDailyCalls = computed(() => {
  const daily = stats.value?.daily ?? []
  return Math.max(1, ...daily.map((day) => day.totalCalls))
})

onMounted(() => {
  void loadLogs()
  void loadStats()
})

function pad(value: number): string {
  return String(value).padStart(2, '0')
}

function toLocalDateTime(date: Date): string {
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    `T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
  )
}

/** 默认筛选：含今天在内的最近 7 天 */
function defaultRange(): [string, string] {
  const end = new Date()
  const start = new Date()
  start.setDate(start.getDate() - 6)
  start.setHours(0, 0, 0, 0)
  return [toLocalDateTime(start), toLocalDateTime(end)]
}

async function loadLogs(): Promise<void> {
  loading.value = true
  try {
    const result = await listLogs({
      page: page.value,
      size: size.value,
      startTime: filters.range?.[0],
      endTime: filters.range?.[1],
      httpStatus: filters.httpStatus,
    })
    items.value = result.records
    total.value = result.total
    page.value = result.pageNum
  } catch (error) {
    ElMessage.error(toErrorMessage(error))
  } finally {
    loading.value = false
  }
}

async function loadStats(): Promise<void> {
  statsLoading.value = true
  try {
    stats.value = await getLogStats()
  } catch (error) {
    ElMessage.error(toErrorMessage(error))
  } finally {
    statsLoading.value = false
  }
}

function refreshAll(): void {
  void loadLogs()
  void loadStats()
}

function applyFilters(): void {
  page.value = 1
  void loadLogs()
}

function resetFilters(): void {
  filters.range = defaultRange()
  filters.httpStatus = undefined
  page.value = 1
  void loadLogs()
}

function handlePageChange(value: number): void {
  page.value = value
  void loadLogs()
}

function handleSizeChange(value: number): void {
  size.value = value
  page.value = 1
  void loadLogs()
}

function httpStatusType(code: number): StatusTagType {
  if (code >= 200 && code < 300) {
    return 'success'
  }
  if (code >= 400 && code < 500) {
    return 'warning'
  }
  if (code >= 500) {
    return 'danger'
  }
  return 'info'
}

function formatLatency(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return '—'
  }
  if (value >= 1000) {
    return `${(value / 1000).toFixed(2)} s`
  }
  return `${value} ms`
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

function shortDate(value: string): string {
  return value.slice(5)
}

function barHeight(calls: number): string {
  return `${Math.max((calls / maxDailyCalls.value) * 100, calls > 0 ? 4 : 0)}%`
}

function barTooltip(day: {
  date: string
  totalCalls: number
  successCalls: number
  clientErrorCalls: number
  serverErrorCalls: number
  avgLatencyMs: number
}): string {
  return (
    `${day.date}：共 ${formatNumber(day.totalCalls)} 次，成功 ${formatNumber(day.successCalls)} 次，` +
    `4xx ${formatNumber(day.clientErrorCalls)} 次，5xx ${formatNumber(day.serverErrorCalls)} 次，` +
    `平均 ${formatLatency(day.avgLatencyMs)}`
  )
}
</script>

<template>
  <div class="logs-page">
    <div class="page-head">
      <div>
        <h2>调用日志</h2>
        <p>
          记录每次开放接口调用的状态码与耗时。时间筛选通过
          <code>create_time</code> 范围查询命中 <code>idx_user_time</code> 索引，默认展示最近 7 天。
        </p>
      </div>
      <el-button :icon="Refresh" :loading="loading || statsLoading" @click="refreshAll">
        刷新
      </el-button>
    </div>

    <section class="stat-grid">
      <el-card shadow="never" class="stat-card">
        <p class="stat-label">近 7 天调用</p>
        <p class="stat-value">
          {{ formatNumber(stats?.totalCalls ?? 0) }}<small> 次</small>
        </p>
        <p class="stat-hint">
          {{ stats ? `${stats.startDate} ~ ${stats.endDate}` : '统计加载中…' }}
        </p>
      </el-card>

      <el-card shadow="never" class="stat-card">
        <p class="stat-label">成功率</p>
        <p class="stat-value">{{ (stats?.successRate ?? 0).toFixed(1) }}<small> %</small></p>
        <el-progress
          :percentage="stats?.successRate ?? 0"
          :stroke-width="5"
          :show-text="false"
          color="#10b981"
          class="stat-progress"
        />
        <p class="stat-hint">成功 {{ formatNumber(stats?.successCalls ?? 0) }} 次</p>
      </el-card>

      <el-card shadow="never" class="stat-card">
        <p class="stat-label">平均耗时</p>
        <p class="stat-value">{{ formatLatency(stats?.avgLatencyMs ?? 0) }}</p>
        <p class="stat-hint">服务端处理 + 计量写入</p>
      </el-card>

      <el-card shadow="never" class="stat-card">
        <p class="stat-label">错误调用</p>
        <p class="stat-value">
          {{ formatNumber((stats?.clientErrorCalls ?? 0) + (stats?.serverErrorCalls ?? 0)) }}
          <small> 次</small>
        </p>
        <p class="stat-hint">
          4xx {{ formatNumber(stats?.clientErrorCalls ?? 0) }} · 5xx
          {{ formatNumber(stats?.serverErrorCalls ?? 0) }}
        </p>
      </el-card>
    </section>

    <el-card v-loading="statsLoading" shadow="never" class="chart-card">
      <template #header>
        <div class="card-head">
          <div>
            <p class="eyebrow">LAST 7 DAYS</p>
            <h3>每日调用趋势</h3>
          </div>
          <div class="legend">
            <span class="legend-item"><i class="dot is-success" />成功</span>
            <span class="legend-item"><i class="dot is-error" />错误</span>
          </div>
        </div>
      </template>

      <div class="chart">
        <div v-for="day in stats?.daily ?? []" :key="day.date" class="bar-column">
          <span class="bar-value">{{ formatNumber(day.totalCalls) }}</span>
          <el-tooltip placement="top" :content="barTooltip(day)">
            <div class="bar-track">
              <div class="bar-segment is-success" :style="{ height: barHeight(day.successCalls) }" />
              <div
                class="bar-segment is-error"
                :style="{ height: barHeight(day.clientErrorCalls + day.serverErrorCalls) }"
              />
            </div>
          </el-tooltip>
          <span class="bar-label">{{ shortDate(day.date) }}</span>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <div class="filter-bar">
        <el-date-picker
          v-model="filters.range"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          value-format="YYYY-MM-DDTHH:mm:ss"
          :default-time="[new Date(2000, 0, 1, 0, 0, 0), new Date(2000, 0, 1, 23, 59, 59)]"
          class="range-picker"
        />
        <el-select
          v-model="filters.httpStatus"
          clearable
          placeholder="全部状态码"
          class="status-select"
        >
          <el-option
            v-for="code in STATUS_OPTIONS"
            :key="code"
            :label="`HTTP ${code}`"
            :value="code"
          />
        </el-select>
        <el-button type="primary" :icon="Search" @click="applyFilters">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="items"
        stripe
        class="logs-table"
        empty-text="所选时间范围内没有调用日志"
      >
        <el-table-column label="调用时间" width="170">
          <template #default="{ row }">
            <span class="time-text">{{ formatTime(row.createTime) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="接口路径" min-width="220">
          <template #default="{ row }">
            <code class="path-code">{{ row.endpointPath }}</code>
          </template>
        </el-table-column>

        <el-table-column label="状态码" width="115">
          <template #default="{ row }">
            <el-tag :type="httpStatusType(row.httpStatus)" effect="light" round size="small">
              HTTP {{ row.httpStatus }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="耗时" width="110">
          <template #default="{ row }">
            <span :class="['latency-text', { 'is-slow': row.latencyMs >= 1000 }]">
              {{ formatLatency(row.latencyMs) }}
            </span>
          </template>
        </el-table-column>

        <el-table-column label="Key ID" width="120">
          <template #default="{ row }">
            <span class="meta-text">#{{ row.apiKeyId }}</span>
          </template>
        </el-table-column>

        <el-table-column label="客户端 IP" width="150">
          <template #default="{ row }">
            <span class="meta-text">{{ row.clientIp ?? '—' }}</span>
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
  </div>
</template>

<style scoped>
.logs-page {
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

.page-head code {
  padding: 0.12rem 0.35rem;
  border-radius: 5px;
  background: #eef2ff;
  color: #4338ca;
  font-size: 0.78rem;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 1rem;
}

.stat-card {
  border: 1px solid #e2e8f0;
  border-radius: 14px;
}

.stat-label {
  margin: 0;
  font-size: 0.72rem;
  letter-spacing: 0.06em;
  color: #94a3b8;
}

.stat-value {
  margin: 0.45rem 0 0;
  font-size: 1.65rem;
  font-weight: 700;
  line-height: 1.15;
  color: #0f172a;
}

.stat-value small {
  font-size: 0.76rem;
  font-weight: 500;
  color: #94a3b8;
}

.stat-hint {
  margin: 0.4rem 0 0;
  font-size: 0.72rem;
  color: #94a3b8;
}

.stat-progress {
  margin-top: 0.5rem;
}

.chart-card,
.table-card {
  border: 1px solid #e2e8f0;
  border-radius: 14px;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.eyebrow {
  margin: 0 0 0.2rem;
  font-size: 0.64rem;
  letter-spacing: 0.18em;
  color: #94a3b8;
}

.card-head h3 {
  margin: 0;
  font-size: 1rem;
  font-weight: 700;
  color: #0f172a;
}

.legend {
  display: flex;
  align-items: center;
  gap: 1rem;
  font-size: 0.72rem;
  color: #64748b;
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
}

.dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.dot.is-success {
  background: #10b981;
}

.dot.is-error {
  background: #ef4444;
}

.chart {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: clamp(0.5rem, 2vw, 1.4rem);
  align-items: end;
  min-height: 190px;
  padding: 0.5rem 0.25rem 0;
}

.bar-column {
  display: grid;
  justify-items: center;
  gap: 0.4rem;
  min-width: 0;
}

.bar-value {
  font-size: 0.7rem;
  color: #94a3b8;
}

.bar-track {
  display: flex;
  flex-direction: column-reverse;
  justify-content: flex-start;
  width: min(100%, 46px);
  height: 130px;
  overflow: hidden;
  border-radius: 8px;
  background: #f1f5f9;
  transition: background 0.18s ease;
}

.bar-track:hover {
  background: #e2e8f0;
}

.bar-segment {
  width: 100%;
  transition: height 0.35s ease;
}

.bar-segment.is-success {
  background: linear-gradient(180deg, #34d399, #10b981);
}

.bar-segment.is-error {
  background: linear-gradient(180deg, #f87171, #ef4444);
}

.bar-label {
  font-size: 0.7rem;
  color: #64748b;
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 1rem;
}

.range-picker {
  flex: 1 1 320px;
  max-width: 420px;
}

.status-select {
  width: 160px;
}

.logs-table {
  --el-table-header-bg-color: #f8fafc;
  --el-table-border-color: #eef2f7;
}

.path-code {
  padding: 0.14rem 0.4rem;
  border-radius: 5px;
  background: #f1f5f9;
  color: #334155;
  font-size: 0.74rem;
  word-break: break-all;
}

.latency-text {
  font-size: 0.78rem;
  font-weight: 600;
  color: #334155;
}

.latency-text.is-slow {
  color: #dc2626;
}

.time-text,
.meta-text {
  font-size: 0.78rem;
  color: #64748b;
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
  padding-top: 1rem;
}

@media (max-width: 1080px) {
  .stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .page-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .stat-grid {
    grid-template-columns: 1fr;
  }

  .chart {
    gap: 0.35rem;
  }

  .range-picker,
  .status-select {
    width: 100%;
    max-width: none;
  }
}
</style>

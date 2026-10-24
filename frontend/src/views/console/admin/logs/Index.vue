<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { CircleCheck, Coin, DataLine, Refresh, Search, Timer, User } from '@element-plus/icons-vue'
import * as echarts from 'echarts/core'
import { BarChart, LineChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { EChartsCoreOption } from 'echarts/core'
import {
  getAdminLogOverview,
  pageAdminLogs,
  type AdminLogItem,
  type AdminLogOverview,
  type AdminLogPageFilters,
  type DailyTrendItem,
  type EndpointRankItem,
} from '@/api/adminLog'
import { usePagination } from '@/composables/usePagination'
import { toErrorMessage } from '@/utils/error'

echarts.use([BarChart, LineChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

defineOptions({ name: 'AdminLogsView' })

type PresetKey = 'today' | 'week' | 'month'
type TagType = 'success' | 'warning' | 'danger' | 'info'
type ProgressStatus = 'success' | 'warning' | 'exception'

interface PresetOption {
  key: PresetKey
  label: string
  days: number
}

interface LogFilters {
  username: string
  endpointPath: string
  httpStatus: number | undefined
  minLatencyMs: string
}

const PRESETS: PresetOption[] = [
  { key: 'today', label: '今日', days: 0 },
  { key: 'week', label: '近 7 天', days: 6 },
  { key: 'month', label: '近 30 天', days: 29 },
]

const STATUS_OPTIONS = [200, 401, 402, 403, 429, 500]

const DEFAULT_TIME: [Date, Date] = [
  new Date(2000, 0, 1, 0, 0, 0),
  new Date(2000, 0, 1, 23, 59, 59),
]

const range = ref<[string, string]>(presetRange(6))
let appliedRange: [string, string] = range.value
const activePreset = ref<PresetKey | null>('week')

const overview = ref<AdminLogOverview | null>(null)
const overviewLoading = ref(false)

const filters = reactive<LogFilters>({
  username: '',
  endpointPath: '',
  httpStatus: undefined,
  minLatencyMs: '',
})

const {
  records: logRecords,
  total: logTotal,
  pageNum: logPageNum,
  pageSize: logPageSize,
  pageSizes: logPageSizes,
  loading: logsLoading,
  search: searchLogs,
  handleCurrentChange: handlePageChange,
  handleSizeChange,
} = usePagination<AdminLogItem, AdminLogPageFilters>(pageAdminLogs, buildPageQuery, {
  pageSize: 20,
  pageSizes: [10, 20, 50, 100],
  onError: (error) => ElMessage.error(toErrorMessage(error)),
})

const rangeText = computed(() =>
  range.value.map((item) => item.replace('T', ' ')).join(' ~ '),
)

const chartEl = ref<HTMLDivElement | null>(null)
const detailSection = ref<HTMLElement | null>(null)
let chart: ReturnType<typeof echarts.init> | null = null
let chartResizeObserver: ResizeObserver | null = null

watch(overview, () => {
  void nextTick(renderChart)
})

onMounted(() => {
  window.addEventListener('resize', handleResize)
  if (chartEl.value) {
    // 侧边栏折叠不会触发 window.resize，额外监听容器尺寸变化
    chartResizeObserver = new ResizeObserver(handleResize)
    chartResizeObserver.observe(chartEl.value)
  }
  void loadOverview()
  searchLogs()
  void nextTick(renderChart)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  chartResizeObserver?.disconnect()
  chartResizeObserver = null
  chart?.dispose()
  chart = null
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

/** daysBack=0 表示今天；起止均为本地时间，后端按 LocalDateTime 解析 */
function presetRange(daysBack: number): [string, string] {
  const end = new Date()
  end.setHours(23, 59, 59, 0)
  const start = new Date()
  start.setDate(start.getDate() - daysBack)
  start.setHours(0, 0, 0, 0)
  return [toLocalDateTime(start), toLocalDateTime(end)]
}

function isRangeAllowed(value: [string, string] | null): boolean {
  if (!value || !value[0] || !value[1]) {
    return false
  }
  const start = new Date(value[0])
  const end = new Date(value[1])
  if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime())) {
    return false
  }
  const startDay = new Date(start.getFullYear(), start.getMonth(), start.getDate()).getTime()
  const endDay = new Date(end.getFullYear(), end.getMonth(), end.getDate()).getTime()
  const span = Math.round((endDay - startDay) / 86_400_000)
  return value[0] <= value[1] && span >= 0 && span < 31
}

function applyPreset(key: PresetKey, days: number): void {
  const next = presetRange(days)
  activePreset.value = key
  range.value = next
  appliedRange = next
  void loadOverview()
  searchLogs()
}

function handleRangeChange(): void {
  if (!isRangeAllowed(range.value)) {
    ElMessage.warning('统计跨度不能超过 31 天')
    range.value = appliedRange
    return
  }
  appliedRange = range.value
  activePreset.value = null
  void loadOverview()
  searchLogs()
}

function refreshAll(): void {
  void loadOverview()
  searchLogs()
}

async function loadOverview(): Promise<void> {
  overviewLoading.value = true
  try {
    const [startTime, endTime] = range.value
    overview.value = await getAdminLogOverview({ startTime, endTime })
  } catch (error) {
    ElMessage.error(toErrorMessage(error))
  } finally {
    overviewLoading.value = false
  }
}

function buildPageQuery(): AdminLogPageFilters {
  const [startTime, endTime] = range.value
  const minLatency = filters.minLatencyMs.trim()
  const parsedLatency = Number(minLatency)

  return {
    startTime,
    endTime,
    username: filters.username.trim() || undefined,
    endpointPath: filters.endpointPath.trim() || undefined,
    httpStatus: filters.httpStatus,
    minLatencyMs:
      minLatency === '' || !Number.isFinite(parsedLatency)
        ? undefined
        : Math.max(0, parsedLatency),
  }
}

function resetFilters(): void {
  filters.username = ''
  filters.endpointPath = ''
  filters.httpStatus = undefined
  filters.minLatencyMs = ''
  searchLogs()
}

/** 点击接口排行：把路径带入明细筛选并滚动到明细区 */
function handleEndpointClick(row: EndpointRankItem): void {
  filters.endpointPath = row.endpointPath
  searchLogs()
  void nextTick(() => {
    detailSection.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  })
}

function handleResize(): void {
  chart?.resize()
}

function renderChart(): void {
  if (!chartEl.value) {
    return
  }
  if (!chart) {
    chart = echarts.init(chartEl.value)
  }
  chart.setOption(buildChartOption(overview.value?.dailyTrend ?? []), true)
}

function buildChartOption(daily: DailyTrendItem[]): EChartsCoreOption {
  const dates = daily.map((item) => item.date)
  const successCalls = daily.map((item) => item.successCalls)
  const anomalyCalls = daily.map((item) => Math.max(0, item.totalCalls - item.successCalls))
  const costPoints = daily.map((item) => item.costPoints)

  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      backgroundColor: 'rgba(15, 23, 42, 0.92)',
      borderWidth: 0,
      textStyle: { color: '#f8fafc', fontSize: 12 },
    },
    legend: {
      left: 0,
      top: 0,
      itemWidth: 10,
      itemHeight: 10,
      textStyle: { color: '#64748b', fontSize: 11 },
    },
    grid: { top: 44, left: 8, right: 8, bottom: 8, containLabel: true },
    xAxis: {
      type: 'category',
      data: dates,
      axisLine: { lineStyle: { color: '#e2e8f0' } },
      axisTick: { show: false },
      axisLabel: {
        color: '#94a3b8',
        fontSize: 11,
        formatter: (value: string) => value.slice(5),
      },
    },
    yAxis: [
      {
        type: 'value',
        name: '请求数',
        minInterval: 1,
        nameTextStyle: { color: '#94a3b8', fontSize: 11 },
        splitLine: { lineStyle: { color: '#f1f5f9' } },
        axisLabel: { color: '#94a3b8', fontSize: 11 },
      },
      {
        type: 'value',
        name: '消耗点数',
        minInterval: 1,
        nameTextStyle: { color: '#94a3b8', fontSize: 11 },
        splitLine: { show: false },
        axisLabel: { color: '#94a3b8', fontSize: 11 },
      },
    ],
    series: [
      {
        name: '成功请求',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 5,
        data: successCalls,
        itemStyle: { color: '#10b981' },
        lineStyle: { width: 2 },
        areaStyle: { color: 'rgba(16, 185, 129, 0.08)' },
        z: 3,
      },
      {
        name: '异常请求',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 5,
        data: anomalyCalls,
        itemStyle: { color: '#ef4444' },
        lineStyle: { width: 2 },
        z: 3,
      },
      {
        name: '消耗点数',
        type: 'bar',
        yAxisIndex: 1,
        barMaxWidth: 22,
        data: costPoints,
        itemStyle: { color: 'rgba(99, 102, 241, 0.55)', borderRadius: [4, 4, 0, 0] },
        z: 1,
      },
    ],
  }
}

function formatNumber(value: number | null | undefined): string {
  return (value ?? 0).toLocaleString('zh-CN')
}

function formatPercent(value: number | null | undefined): string {
  return (value ?? 0).toFixed(1)
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

function statusTagType(code: number): TagType {
  if (code >= 200 && code < 300) {
    return 'success'
  }
  if (code === 429) {
    return 'warning'
  }
  if (code >= 500) {
    return 'danger'
  }
  if (code >= 400) {
    return 'warning'
  }
  return 'info'
}

function latencyClass(value: number): string {
  if (value < 200) {
    return 'is-fast'
  }
  if (value <= 800) {
    return 'is-mid'
  }
  return 'is-slow'
}

function progressStatus(rate: number): ProgressStatus {
  if (rate >= 99) {
    return 'success'
  }
  if (rate >= 95) {
    return 'warning'
  }
  return 'exception'
}
</script>

<template>
  <div class="admin-logs-page">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="preset-group">
          <el-button
            v-for="preset in PRESETS"
            :key="preset.key"
            :type="activePreset === preset.key ? 'primary' : 'default'"
            @click="applyPreset(preset.key, preset.days)"
          >
            {{ preset.label }}
          </el-button>
        </div>

        <el-date-picker
          v-model="range"
          type="datetimerange"
          :clearable="false"
          :default-time="DEFAULT_TIME"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          value-format="YYYY-MM-DDTHH:mm:ss"
          class="range-picker"
          @change="handleRangeChange"
        />

        <el-button :icon="Refresh" :loading="overviewLoading || logsLoading" @click="refreshAll">
          刷新
        </el-button>

        <span class="range-hint">统计区间：{{ rangeText }}</span>
      </div>
    </el-card>

    <section class="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
      <el-card v-loading="overviewLoading" shadow="never" class="kpi-card h-full">
        <div class="kpi-head">
          <span class="kpi-label">总调用量</span>
          <el-icon class="kpi-icon is-indigo"><DataLine /></el-icon>
        </div>
        <p class="kpi-value">
          {{ formatNumber(overview?.totalCalls ?? 0) }}<small> 次</small>
        </p>
        <p class="kpi-hint">统计区间内全站调用</p>
      </el-card>

      <el-card v-loading="overviewLoading" shadow="never" class="kpi-card h-full">
        <div class="kpi-head">
          <span class="kpi-label">累计消耗点数</span>
          <el-icon class="kpi-icon is-amber"><Coin /></el-icon>
        </div>
        <p class="kpi-value">
          {{ formatNumber(overview?.totalCostPoints ?? 0) }}<small> 点</small>
        </p>
        <p class="kpi-hint">SUM(cost_points)</p>
      </el-card>

      <el-card v-loading="overviewLoading" shadow="never" class="kpi-card h-full">
        <div class="kpi-head">
          <span class="kpi-label">整体成功率</span>
          <el-icon class="kpi-icon is-emerald"><CircleCheck /></el-icon>
        </div>
        <p class="kpi-value">
          {{ formatPercent(overview?.successRate ?? 0) }}<small> %</small>
        </p>
        <el-progress
          :percentage="Math.min(100, Math.max(0, overview?.successRate ?? 0))"
          :stroke-width="5"
          :show-text="false"
          :status="progressStatus(overview?.successRate ?? 0)"
          class="kpi-progress"
        />
        <p class="kpi-hint kpi-error">
          <span class="is-warning">429 限流 {{ formatNumber(overview?.rateLimitCount ?? 0) }}</span>
          <span>·</span>
          <span class="is-danger">5xx 异常 {{ formatNumber(overview?.error5xxCount ?? 0) }}</span>
        </p>
      </el-card>

      <el-card v-loading="overviewLoading" shadow="never" class="kpi-card h-full">
        <div class="kpi-head">
          <span class="kpi-label">平均响应耗时</span>
          <el-icon class="kpi-icon is-sky"><Timer /></el-icon>
        </div>
        <p class="kpi-value">{{ formatLatency(overview?.avgLatencyMs ?? 0) }}</p>
        <p class="kpi-hint">AVG(latency_ms)</p>
      </el-card>
    </section>

    <section class="grid grid-cols-1 gap-4 xl:grid-cols-12">
      <el-card v-loading="overviewLoading" shadow="never" class="chart-card xl:col-span-6 2xl:col-span-7">
        <template #header>
          <div class="card-head">
            <div>
              <p class="eyebrow">TREND</p>
              <h3>每日调用趋势</h3>
            </div>
            <span class="card-sub">左轴请求数 · 右轴消耗点数</span>
          </div>
        </template>
        <div ref="chartEl" class="chart-canvas" />
      </el-card>

      <el-card v-loading="overviewLoading" shadow="never" class="rank-card xl:col-span-6 2xl:col-span-5">
        <template #header>
          <div class="card-head">
            <div>
              <p class="eyebrow">TOP 10</p>
              <h3>接口健康排行</h3>
            </div>
            <span class="rank-tip">点击行下钻明细</span>
          </div>
        </template>

        <el-table
          :data="overview?.endpointRank ?? []"
          size="small"
          :row-style="{ cursor: 'pointer' }"
          empty-text="暂无接口调用"
          @row-click="handleEndpointClick"
        >
          <el-table-column label="接口" min-width="130">
            <template #default="{ row }">
              <div class="endpoint-cell">
                <span class="endpoint-name" :title="row.endpointName">{{ row.endpointName }}</span>
                <code class="endpoint-path" :title="row.endpointPath">{{ row.endpointPath }}</code>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="调用量" width="60" align="right">
            <template #default="{ row }">
              <span class="rank-calls">{{ formatNumber(row.callCount) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="点数" width="54" align="right">
            <template #default="{ row }">
              <span class="meta-text">{{ formatNumber(row.costPoints) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="成功率" width="104">
            <template #default="{ row }">
              <div class="rate-cell">
                <el-progress
                  :percentage="Math.min(100, Math.max(0, row.successRate))"
                  :stroke-width="6"
                  :show-text="false"
                  :status="progressStatus(row.successRate)"
                  class="rate-bar"
                />
                <span class="rate-text">{{ formatPercent(row.successRate) }}%</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="平均耗时" width="72" align="right">
            <template #default="{ row }">
              <span class="meta-text">{{ formatLatency(row.avgLatencyMs) }}</span>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </section>

    <section ref="detailSection">
      <el-card shadow="never" class="detail-card">
        <template #header>
          <div class="card-head">
            <div>
              <p class="eyebrow">CALL LOGS</p>
              <h3>全站调用日志明细</h3>
            </div>
            <span class="card-sub">共 {{ formatNumber(logTotal) }} 条</span>
          </div>
        </template>

        <div class="filter-bar">
          <el-input
            v-model="filters.username"
            clearable
            placeholder="用户名"
            :prefix-icon="User"
            class="filter-input"
            @keyup.enter="searchLogs"
          />
          <el-input
            v-model="filters.endpointPath"
            clearable
            placeholder="接口路径，如 /api/v1/services/echo"
            class="filter-input is-wide"
            @keyup.enter="searchLogs"
          />
          <el-select
            v-model="filters.httpStatus"
            clearable
            placeholder="全部状态码"
            class="filter-select"
          >
            <el-option
              v-for="code in STATUS_OPTIONS"
              :key="code"
              :label="`HTTP ${code}`"
              :value="code"
            />
          </el-select>
          <el-input
            v-model="filters.minLatencyMs"
            clearable
            placeholder="最小耗时"
            class="filter-input is-latency"
            @keyup.enter="searchLogs"
          >
            <template #suffix><span class="input-suffix">ms</span></template>
          </el-input>
          <el-button type="primary" :icon="Search" @click="searchLogs">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </div>

        <el-table
          v-loading="logsLoading"
          :data="logRecords"
          stripe
          class="logs-table"
          empty-text="当前筛选条件下没有调用日志"
        >
          <el-table-column label="调用时间" width="170">
            <template #default="{ row }">
              <span class="time-text">{{ formatTime(row.createTime) }}</span>
            </template>
          </el-table-column>

          <el-table-column label="用户名" width="130">
            <template #default="{ row }">
              <span class="meta-text">{{ row.username ?? '—' }}</span>
            </template>
          </el-table-column>

          <el-table-column label="API Key 前缀" width="188">
            <template #default="{ row }">
              <code class="mono-cell">{{ row.keyPrefix ?? '—' }}</code>
            </template>
          </el-table-column>

          <el-table-column label="接口路径" min-width="230">
            <template #default="{ row }">
              <code class="path-code">{{ row.endpointPath }}</code>
            </template>
          </el-table-column>

          <el-table-column label="状态码" width="112">
            <template #default="{ row }">
              <el-tag
                :type="statusTagType(row.httpStatus)"
                effect="light"
                round
                size="small"
              >
                HTTP {{ row.httpStatus }}
              </el-tag>
            </template>
          </el-table-column>

          <el-table-column label="本次扣减点数" width="112" align="right">
            <template #default="{ row }">
              <span class="cost-text">{{ formatNumber(row.costPoints) }}</span>
            </template>
          </el-table-column>

          <el-table-column label="耗时" width="100">
            <template #default="{ row }">
              <span :class="['latency-text', latencyClass(row.latencyMs)]">
                {{ formatLatency(row.latencyMs) }}
              </span>
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
            :total="logTotal"
            :page-size="logPageSize"
            :current-page="logPageNum"
            :page-sizes="logPageSizes"
            @current-change="handlePageChange"
            @size-change="handleSizeChange"
          />
        </div>
      </el-card>
    </section>
  </div>
</template>

<style scoped>
.admin-logs-page {
  display: grid;
  gap: 1.25rem;
}

/* 表格固定列宽之和可能超过内容区，约束 grid 项避免撑出页面级横向滚动 */
.admin-logs-page > section {
  min-width: 0;
}

.toolbar-card,
.kpi-card,
.chart-card,
.rank-card,
.detail-card {
  min-width: 0;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem;
}

.preset-group {
  display: flex;
  gap: 0.5rem;
}

.range-picker {
  width: 360px;
}

.range-hint {
  margin-left: auto;
  font-size: 0.72rem;
  color: #94a3b8;
}

.kpi-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}

.kpi-label {
  font-size: 0.72rem;
  letter-spacing: 0.06em;
  color: #94a3b8;
}

.kpi-icon {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 9px;
  font-size: 0.95rem;
}

.kpi-icon.is-indigo {
  background: #eef2ff;
  color: #4f46e5;
}

.kpi-icon.is-amber {
  background: #fffbeb;
  color: #d97706;
}

.kpi-icon.is-emerald {
  background: #ecfdf5;
  color: #059669;
}

.kpi-icon.is-sky {
  background: #eff6ff;
  color: #0284c7;
}

.kpi-value {
  margin: 0.55rem 0 0;
  font-size: 1.65rem;
  font-weight: 700;
  line-height: 1.15;
  color: #0f172a;
}

.kpi-value small {
  font-size: 0.76rem;
  font-weight: 500;
  color: #94a3b8;
}

.kpi-hint {
  margin: 0.4rem 0 0;
  font-size: 0.72rem;
  color: #94a3b8;
}

.kpi-progress {
  margin-top: 0.5rem;
}

.kpi-error {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
}

.kpi-error .is-warning {
  color: #d97706;
}

.kpi-error .is-danger {
  color: #e11d48;
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

.card-sub {
  font-size: 0.7rem;
  color: #94a3b8;
}

.rank-tip {
  font-size: 0.7rem;
  color: #6366f1;
}

.chart-canvas {
  width: 100%;
  height: 320px;
}

.endpoint-cell {
  display: grid;
  gap: 0.12rem;
  min-width: 0;
}

.endpoint-name {
  overflow: hidden;
  font-size: 0.78rem;
  font-weight: 600;
  color: #0f172a;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.endpoint-path {
  overflow: hidden;
  font-size: 0.64rem;
  color: #94a3b8;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rank-calls {
  font-size: 0.78rem;
  font-weight: 600;
  color: #0f172a;
}

.rate-cell {
  display: flex;
  align-items: center;
  gap: 0.4rem;
}

.rate-bar {
  flex: 1;
  min-width: 46px;
}

.rate-text {
  flex: none;
  font-size: 0.68rem;
  color: #64748b;
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 1rem;
}

.filter-input {
  width: 180px;
}

.filter-input.is-wide {
  width: 280px;
}

.filter-input.is-latency {
  width: 180px;
}

.filter-select {
  width: 150px;
}

.input-suffix {
  font-size: 0.7rem;
  color: #94a3b8;
}

.logs-table {
  --el-table-header-bg-color: #f8fafc;
  --el-table-border-color: #eef2f7;
}

.mono-cell {
  padding: 0.14rem 0.4rem;
  border-radius: 5px;
  background: #f1f5f9;
  color: #475569;
  font-family: 'JetBrains Mono', 'SFMono-Regular', Menlo, Consolas, monospace;
  font-size: 0.72rem;
  white-space: nowrap;
}

.path-code {
  padding: 0.14rem 0.4rem;
  border-radius: 5px;
  background: #f1f5f9;
  color: #334155;
  font-size: 0.74rem;
  word-break: break-all;
}

.cost-text {
  font-size: 0.78rem;
  font-weight: 600;
  color: #4f46e5;
}

.latency-text {
  font-size: 0.78rem;
  font-weight: 600;
}

.latency-text.is-fast {
  color: #059669;
}

.latency-text.is-mid {
  color: #d97706;
}

.latency-text.is-slow {
  color: #dc2626;
  font-weight: 700;
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

@media (max-width: 900px) {
  .range-picker {
    width: 100%;
  }

  .range-hint {
    margin-left: 0;
  }
}

@media (max-width: 640px) {
  .toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .preset-group {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .filter-input,
  .filter-input.is-wide,
  .filter-input.is-latency,
  .filter-select {
    width: 100%;
  }

  .chart-canvas {
    height: 260px;
  }
}
</style>

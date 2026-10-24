<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Check, Refresh, Wallet } from '@element-plus/icons-vue'
import QrcodeVue from 'qrcode.vue'
import {
  createRechargeOrder,
  getRechargeOrderStatus,
  listRechargeOrders,
  listRechargePackages,
  mockPayRechargeOrder,
  type PayChannel,
  type RechargeOrderCreateResult,
  type RechargeOrderItem,
  type RechargeOrderStatus,
  type RechargeOrderStatusResult,
  type RechargePackage,
} from '@/api/recharge'
import { usePagination } from '@/composables/usePagination'
import { useAuthStore } from '@/stores/auth'
import { toErrorMessage } from '@/utils/error'
import { ApiError } from '@/utils/request'

defineOptions({ name: 'RechargeView' })

type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'

const auth = useAuthStore()

const packages = ref<RechargePackage[]>([])
const packagesLoading = ref(false)
const selectedPackageId = ref<string | null>(null)
const selectedChannel = ref<PayChannel>('ALIPAY')
const creating = ref(false)

const dialogVisible = ref(false)
const orderInfo = ref<RechargeOrderCreateResult | null>(null)
const remainingSeconds = ref(0)
const mockPaying = ref(false)
const statusRequesting = ref(false)

let pollTimer: ReturnType<typeof window.setInterval> | null = null
let countdownTimer: ReturnType<typeof window.setInterval> | null = null
/** 防止轮询与沙箱支付同时完成时重复提示、重复刷新 */
let paidHandled = false

const {
  records: orders,
  total,
  pageNum,
  pageSize,
  pageSizes,
  loading: ordersLoading,
  load: loadOrders,
  search: searchOrders,
  handleCurrentChange,
  handleSizeChange,
} = usePagination<RechargeOrderItem, { status?: RechargeOrderStatus }>(
  (query) => listRechargeOrders(query),
  () => ({}),
  {
    pageSize: 10,
    onError: (error) => ElMessage.error(toErrorMessage(error)),
  },
)

const balance = computed(() => auth.balanceQuota)
const selectedPackage = computed(
  () => packages.value.find((item) => item.packageId === selectedPackageId.value) ?? null,
)
const payableYuan = computed(() => formatYuan(selectedPackage.value?.amountFen))
const expectedPoints = computed(() => selectedPackage.value?.totalPoints ?? 0)
const expectedBonus = computed(() => selectedPackage.value?.bonusPoints ?? 0)
const canCreate = computed(() => Boolean(selectedPackage.value) && !creating.value)
const dialogTitle = computed(() =>
  orderInfo.value?.payChannel === 'WECHAT' ? '微信扫码支付' : '支付宝扫码支付',
)
const countdownText = computed(() => formatCountdown(remainingSeconds.value))

onMounted(() => {
  void auth.ensureMe().catch(() => undefined)
  void loadPackages()
  void loadOrders()
})

onUnmounted(() => {
  stopTimers()
})

async function loadPackages(): Promise<void> {
  packagesLoading.value = true
  try {
    packages.value = await listRechargePackages()
    const selectedExists = packages.value.some(
      (item) => item.packageId === selectedPackageId.value,
    )
    if (!selectedExists) {
      const preferred = packages.value.find((item) => item.hot) ?? packages.value[0]
      selectedPackageId.value = preferred?.packageId ?? null
    }
  } catch (error) {
    ElMessage.error(toErrorMessage(error))
  } finally {
    packagesLoading.value = false
  }
}

async function refreshBalance(): Promise<void> {
  try {
    await auth.fetchMe()
    ElMessage.success('余额已刷新')
  } catch (error) {
    ElMessage.error(toErrorMessage(error))
  }
}

async function handleCreateOrder(): Promise<void> {
  const pkg = selectedPackage.value
  if (!pkg) {
    ElMessage.warning('请先选择充值档位')
    return
  }
  creating.value = true
  try {
    const result = await createRechargeOrder({
      packageId: pkg.packageId,
      payChannel: selectedChannel.value,
    })
    orderInfo.value = result
    paidHandled = false
    remainingSeconds.value = secondsUntil(result.expireTime)
    dialogVisible.value = true
    startTimers()
    void loadOrders()
  } catch (error) {
    ElMessage.error(toErrorMessage(error))
  } finally {
    creating.value = false
  }
}

function startTimers(): void {
  stopTimers()
  countdownTimer = window.setInterval(() => {
    if (remainingSeconds.value <= 0) {
      return
    }
    remainingSeconds.value -= 1
    if (remainingSeconds.value === 0) {
      void handleExpired()
    }
  }, 1_000)
  pollTimer = window.setInterval(() => {
    void pollOrderStatus()
  }, 2_000)
}

function stopTimers(): void {
  if (pollTimer !== null) {
    window.clearInterval(pollTimer)
    pollTimer = null
  }
  if (countdownTimer !== null) {
    window.clearInterval(countdownTimer)
    countdownTimer = null
  }
}

async function pollOrderStatus(): Promise<void> {
  const current = orderInfo.value
  if (!current || statusRequesting.value || paidHandled) {
    return
  }
  statusRequesting.value = true
  try {
    const result = await getRechargeOrderStatus(current.orderNo)
    if (result.status === 1) {
      await handlePaid(result)
    } else if (result.status === 2) {
      handleClosed()
    }
  } catch (error) {
    // 轮询期间的瞬时网络错误保持静默，鉴权失效或订单不存在时停止轮询
    if (error instanceof ApiError && (error.httpStatus === 401 || error.httpStatus === 404)) {
      stopTimers()
    }
  } finally {
    statusRequesting.value = false
  }
}

async function handlePaid(result: RechargeOrderStatusResult): Promise<void> {
  if (paidHandled) {
    return
  }
  paidHandled = true
  const points = result.totalPoints || orderInfo.value?.totalPoints || 0
  stopTimers()
  dialogVisible.value = false
  ElMessage.success(`充值成功，${formatNumber(points)} 点已到账`)
  await auth.fetchMe().catch(() => null)
  searchOrders()
}

function handleClosed(): void {
  if (paidHandled) {
    return
  }
  stopTimers()
  dialogVisible.value = false
  ElMessage.warning('订单已关闭，请重新发起充值')
  void loadOrders()
}

async function handleExpired(): Promise<void> {
  const current = orderInfo.value
  if (!current || paidHandled) {
    return
  }
  stopTimers()
  try {
    // 由服务端确认最终状态，同时触发过期待支付订单的自动关闭
    const result = await getRechargeOrderStatus(current.orderNo)
    if (result.status === 1) {
      await handlePaid(result)
      return
    }
  } catch {
    // 订单已支付/关闭时查询可能失败，按超时处理即可
  }
  if (paidHandled) {
    return
  }
  dialogVisible.value = false
  ElMessage.warning('二维码已过期，订单已自动关闭')
  void loadOrders()
}

async function handleMockPay(): Promise<void> {
  const current = orderInfo.value
  if (!current || mockPaying.value) {
    return
  }
  mockPaying.value = true
  try {
    const result = await mockPayRechargeOrder(current.orderNo)
    await handlePaid(result)
  } catch (error) {
    ElMessage.error(toErrorMessage(error))
  } finally {
    mockPaying.value = false
  }
}

function handleDialogClose(): void {
  stopTimers()
  orderInfo.value = null
}

function secondsUntil(value: string | null | undefined): number {
  if (!value) {
    return 15 * 60
  }
  // 后端 LocalDateTime 可能带 6 位纳秒，部分浏览器只接受 3 位毫秒
  const normalized = value.replace(/(\.\d{3})\d*$/, '$1')
  const target = new Date(normalized).getTime()
  if (Number.isNaN(target)) {
    return 15 * 60
  }
  return Math.max(0, Math.floor((target - Date.now()) / 1_000))
}

function bonusBadge(pkg: RechargePackage): string {
  if (pkg.bestValue) {
    return '超值赠 50%'
  }
  return `多送 ${formatNumber(pkg.bonusPoints)} 点`
}

function orderStatusType(status: number): TagType {
  if (status === 1) {
    return 'success'
  }
  if (status === 0) {
    return 'warning'
  }
  return 'info'
}

function channelType(channel: PayChannel): TagType {
  return channel === 'ALIPAY' ? 'primary' : 'success'
}

function formatNumber(value: number | null | undefined): string {
  return Number(value ?? 0).toLocaleString('zh-CN')
}

/** 分转元，始终保留两位小数 */
function formatYuan(fen: number | null | undefined): string {
  return (Number(fen ?? 0) / 100).toFixed(2)
}

function formatDateTime(value: string | null | undefined): string {
  if (!value) {
    return '—'
  }
  return value.replace('T', ' ').slice(0, 19)
}

function formatCountdown(seconds: number): string {
  const safe = Math.max(0, Math.floor(seconds))
  const minutes = Math.floor(safe / 60)
  const rest = safe % 60
  return `${String(minutes).padStart(2, '0')}:${String(rest).padStart(2, '0')}`
}
</script>

<template>
  <div class="recharge-page">
    <section class="balance-banner">
      <div class="balance-main">
        <p class="eyebrow">ACCOUNT BALANCE</p>
        <p class="balance-label">剩余可用点数</p>
        <p class="balance-value">
          {{ formatNumber(balance) }}<small>点</small>
        </p>
        <p class="balance-hint">点数用于调用已开通的开放接口，充值成功后即时到账。</p>
      </div>
      <div class="balance-side">
        <div class="balance-icon">
          <el-icon :size="26"><Wallet /></el-icon>
        </div>
        <div class="balance-meta">
          <span>1 元 ≈ 100 点</span>
          <span>支持支付宝 / 微信支付</span>
        </div>
        <button type="button" class="balance-refresh" @click="refreshBalance">
          <el-icon><Refresh /></el-icon>
          刷新余额
        </button>
      </div>
    </section>

    <section class="panel">
      <div class="section-head">
        <div>
          <p class="eyebrow">STEP 01</p>
          <h3>选择充值档位</h3>
        </div>
        <p class="section-hint">充值金额越高，赠送点数越多</p>
      </div>

      <div v-loading="packagesLoading" class="package-grid">
        <button
          v-for="pkg in packages"
          :key="pkg.packageId"
          type="button"
          class="package-card"
          :class="{ 'is-selected': pkg.packageId === selectedPackageId }"
          :aria-pressed="pkg.packageId === selectedPackageId"
          @click="selectedPackageId = pkg.packageId"
        >
          <span
            v-if="pkg.bonusPoints > 0"
            class="package-badge"
            :class="{ 'is-best': pkg.bestValue }"
          >
            {{ bonusBadge(pkg) }}
          </span>

          <div class="package-price-row">
            <span class="package-price"><em>¥</em>{{ pkg.amountFen / 100 }}</span>
            <span v-if="pkg.hot" class="package-hot">热门</span>
          </div>
          <span class="package-points">{{ formatNumber(pkg.totalPoints) }}<small> 点</small></span>
          <span class="package-base">
            基础 {{ formatNumber(pkg.basePoints) }} 点
            <template v-if="pkg.bonusPoints > 0">
              + 赠 {{ formatNumber(pkg.bonusPoints) }}
            </template>
          </span>

          <span class="package-check">
            <el-icon :size="12"><Check /></el-icon>
          </span>
        </button>
      </div>
    </section>

    <section class="panel">
      <div class="section-head">
        <div>
          <p class="eyebrow">STEP 02</p>
          <h3>选择支付方式</h3>
        </div>
        <p class="section-hint">扫码后请尽快完成支付</p>
      </div>

      <div class="channel-grid">
        <button
          type="button"
          class="channel-card"
          :class="{ 'is-selected': selectedChannel === 'ALIPAY' }"
          :aria-pressed="selectedChannel === 'ALIPAY'"
          @click="selectedChannel = 'ALIPAY'"
        >
          <span class="channel-icon is-alipay">支</span>
          <span class="channel-copy">
            <strong>支付宝</strong>
            <small>Alipay · 扫码支付</small>
          </span>
          <span class="channel-radio"><i /></span>
        </button>

        <button
          type="button"
          class="channel-card"
          :class="{ 'is-selected': selectedChannel === 'WECHAT' }"
          :aria-pressed="selectedChannel === 'WECHAT'"
          @click="selectedChannel = 'WECHAT'"
        >
          <span class="channel-icon is-wechat">微</span>
          <span class="channel-copy">
            <strong>微信支付</strong>
            <small>WeChat Pay · 扫码支付</small>
          </span>
          <span class="channel-radio"><i /></span>
        </button>
      </div>

      <div class="checkout-bar">
        <div class="checkout-summary">
          <div class="summary-item">
            <span>应付金额</span>
            <strong class="summary-amount">¥ {{ payableYuan }}</strong>
          </div>
          <div class="summary-divider" />
          <div class="summary-item">
            <span>预计到账</span>
            <strong class="summary-points">{{ formatNumber(expectedPoints) }} 点</strong>
          </div>
          <p v-if="expectedBonus > 0" class="summary-bonus">
            含赠送 {{ formatNumber(expectedBonus) }} 点
          </p>
        </div>
        <el-button
          type="primary"
          size="large"
          class="checkout-button"
          :loading="creating"
          :disabled="!canCreate"
          @click="handleCreateOrder"
        >
          立即充值
        </el-button>
      </div>
    </section>

    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="card-head">
          <div>
            <p class="eyebrow">HISTORY</p>
            <h3>充值记录</h3>
          </div>
          <el-button :icon="Refresh" :loading="ordersLoading" @click="loadOrders">
            刷新
          </el-button>
        </div>
      </template>

      <el-table
        v-loading="ordersLoading"
        :data="orders"
        stripe
        class="orders-table"
        empty-text="暂无充值记录，选择上方档位开始充值"
      >
        <el-table-column label="订单号" min-width="215">
          <template #default="{ row }">
            <code class="order-code">{{ row.orderNo }}</code>
          </template>
        </el-table-column>

        <el-table-column label="套餐点数" width="130">
          <template #default="{ row }">
            <span class="points-text">+{{ formatNumber(row.totalPoints) }} 点</span>
          </template>
        </el-table-column>

        <el-table-column label="金额" width="120">
          <template #default="{ row }">
            <span class="money-text">¥ {{ formatYuan(row.amountFen) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="支付渠道" width="120">
          <template #default="{ row }">
            <el-tag :type="channelType(row.payChannel)" effect="light" round size="small">
              {{ row.payChannelName }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="105">
          <template #default="{ row }">
            <el-tag :type="orderStatusType(row.status)" effect="light" round size="small">
              {{ row.statusDesc }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="支付时间" width="175">
          <template #default="{ row }">
            <span class="time-text">{{ formatDateTime(row.payTime) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="下单时间" width="175">
          <template #default="{ row }">
            <span class="time-text">{{ formatDateTime(row.createTime) }}</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-row">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :total="total"
          :page-size="pageSize"
          :current-page="pageNum"
          :page-sizes="pageSizes"
          @current-change="handleCurrentChange"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="420px"
      align-center
      destroy-on-close
      @close="handleDialogClose"
    >
      <div v-if="orderInfo" class="pay-body">
        <div class="pay-amount">
          <span>应付金额</span>
          <strong>¥ {{ formatYuan(orderInfo.amountFen) }}</strong>
        </div>

        <div class="qr-frame">
          <QrcodeVue :value="orderInfo.qrCodeUrl" :size="200" level="M" :margin="2" />
        </div>

        <p class="qr-hint">请使用{{ orderInfo.payChannelName }}扫描二维码完成支付</p>
        <p class="countdown" :class="{ 'is-danger': remainingSeconds <= 60 }">
          二维码有效期 <strong>{{ countdownText }}</strong>
        </p>
        <p class="order-no">订单号 {{ orderInfo.orderNo }}</p>

        <el-button
          text
          class="mock-pay-button"
          :loading="mockPaying"
          @click="handleMockPay"
        >
          🛠️ 模拟手机扫码支付成功 (Dev)
        </el-button>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.recharge-page {
  display: grid;
  gap: 1.25rem;
}

.eyebrow {
  margin: 0 0 0.25rem;
  font-size: 0.64rem;
  font-weight: 600;
  letter-spacing: 0.18em;
  color: #94a3b8;
}

/* ---------- 余额横幅 ---------- */
.balance-banner {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 2rem;
  padding: 1.75rem 2rem;
  overflow: hidden;
  border-radius: 20px;
  color: #ffffff;
  background:
    radial-gradient(circle at 82% 0%, rgba(56, 189, 248, 0.38), transparent 42%),
    radial-gradient(circle at 12% 100%, rgba(99, 102, 241, 0.42), transparent 45%),
    linear-gradient(135deg, #1e1b4b, #312e81 58%, #0f172a);
  box-shadow: 0 22px 45px -32px rgba(49, 46, 129, 0.9);
}

.balance-banner::after {
  position: absolute;
  top: -70px;
  right: -40px;
  width: 210px;
  height: 210px;
  border: 1px solid rgba(255, 255, 255, 0.09);
  border-radius: 50%;
  content: '';
}

.balance-main {
  position: relative;
  z-index: 1;
}

.balance-banner .eyebrow {
  color: #a5b4fc;
}

.balance-label {
  margin: 0;
  font-size: 0.82rem;
  color: #c7d2fe;
}

.balance-value {
  display: flex;
  align-items: baseline;
  gap: 0.5rem;
  margin: 0.35rem 0 0.4rem;
  font-size: clamp(2.3rem, 4vw, 3rem);
  font-weight: 800;
  letter-spacing: -0.02em;
  line-height: 1.05;
  color: #ffffff;
  text-shadow: 0 12px 30px rgba(129, 140, 248, 0.45);
}

.balance-value small {
  font-size: 0.9rem;
  font-weight: 500;
  letter-spacing: 0;
  color: #a5b4fc;
}

.balance-hint {
  margin: 0;
  font-size: 0.78rem;
  color: #94a3b8;
}

.balance-side {
  position: relative;
  z-index: 1;
  display: grid;
  justify-items: end;
  gap: 0.75rem;
}

.balance-icon {
  display: grid;
  place-items: center;
  width: 54px;
  height: 54px;
  border: 1px solid rgba(255, 255, 255, 0.16);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.08);
  color: #e0e7ff;
}

.balance-meta {
  display: grid;
  gap: 0.2rem;
  font-size: 0.72rem;
  text-align: right;
  color: #a5b4fc;
}

.balance-refresh {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.4rem 0.8rem;
  border: 1px solid rgba(255, 255, 255, 0.16);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.06);
  color: #e2e8f0;
  font-size: 0.74rem;
  cursor: pointer;
  transition: background 0.18s ease, border-color 0.18s ease;
}

.balance-refresh:hover {
  border-color: rgba(165, 180, 252, 0.55);
  background: rgba(255, 255, 255, 0.12);
}

/* ---------- 通用面板 ---------- */
.panel {
  padding: 1.4rem 1.5rem;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  background: #ffffff;
}

.section-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 1.1rem;
}

.section-head h3,
.card-head h3 {
  margin: 0;
  font-size: 1.02rem;
  font-weight: 700;
  color: #0f172a;
}

.section-hint {
  margin: 0;
  font-size: 0.74rem;
  color: #94a3b8;
}

/* ---------- 套餐卡片 ---------- */
.package-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 0.85rem;
  min-height: 150px;
}

.package-card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  padding: 1.45rem 1rem 1rem;
  border: 1.5px solid #e2e8f0;
  border-radius: 14px;
  background: #ffffff;
  text-align: left;
  cursor: pointer;
  transition:
    border-color 0.18s ease,
    background 0.18s ease,
    box-shadow 0.18s ease,
    transform 0.18s ease;
}

.package-card:hover {
  border-color: #a5b4fc;
  transform: translateY(-2px);
}

.package-card.is-selected {
  border-color: #4f46e5;
  background: linear-gradient(180deg, #eef2ff 0%, #ffffff 78%);
  box-shadow:
    0 0 0 3px rgba(79, 70, 229, 0.14),
    0 16px 28px -24px rgba(79, 70, 229, 0.85);
}

.package-badge {
  position: absolute;
  top: 9px;
  right: 9px;
  padding: 0.16rem 0.45rem;
  border-radius: 999px;
  background: #fef3c7;
  color: #b45309;
  font-size: 0.64rem;
  font-weight: 600;
  white-space: nowrap;
}

.package-badge.is-best {
  background: linear-gradient(135deg, #f97316, #ef4444);
  color: #ffffff;
}

.package-price-row {
  display: flex;
  align-items: center;
  gap: 0.4rem;
}

.package-price {
  font-size: 1.5rem;
  font-weight: 800;
  color: #0f172a;
  letter-spacing: -0.02em;
}

.package-price em {
  margin-right: 0.1rem;
  font-size: 0.82rem;
  font-style: normal;
  font-weight: 600;
  color: #64748b;
}

.package-hot {
  padding: 0.08rem 0.36rem;
  border-radius: 999px;
  background: #fee2e2;
  color: #dc2626;
  font-size: 0.6rem;
  font-weight: 600;
}

.package-points {
  font-size: 1rem;
  font-weight: 700;
  color: #4338ca;
}

.package-points small {
  font-size: 0.7rem;
  font-weight: 500;
  color: #818cf8;
}

.package-base {
  font-size: 0.68rem;
  line-height: 1.5;
  color: #94a3b8;
}

.package-check {
  position: absolute;
  right: 10px;
  bottom: 10px;
  display: grid;
  place-items: center;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #4f46e5;
  color: #ffffff;
  opacity: 0;
  transform: scale(0.6);
  transition: opacity 0.18s ease, transform 0.18s ease;
}

.package-card.is-selected .package-check {
  opacity: 1;
  transform: scale(1);
}

/* ---------- 支付方式 ---------- */
.channel-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.9rem;
}

.channel-card {
  display: flex;
  align-items: center;
  gap: 0.85rem;
  padding: 0.95rem 1rem;
  border: 1.5px solid #e2e8f0;
  border-radius: 12px;
  background: #ffffff;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.18s ease, background 0.18s ease, box-shadow 0.18s ease;
}

.channel-card:hover {
  border-color: #a5b4fc;
}

.channel-card.is-selected {
  border-color: #4f46e5;
  background: #f5f7ff;
  box-shadow: 0 0 0 3px rgba(79, 70, 229, 0.12);
}

.channel-icon {
  display: grid;
  place-items: center;
  flex: none;
  width: 42px;
  height: 42px;
  border-radius: 12px;
  font-size: 1.05rem;
  font-weight: 700;
  color: #ffffff;
}

.channel-icon.is-alipay {
  background: linear-gradient(135deg, #1677ff, #0b5ed7);
}

.channel-icon.is-wechat {
  background: linear-gradient(135deg, #22c55e, #15803d);
}

.channel-copy {
  display: grid;
  gap: 0.16rem;
  min-width: 0;
}

.channel-copy strong {
  font-size: 0.9rem;
  font-weight: 700;
  color: #0f172a;
}

.channel-copy small {
  font-size: 0.7rem;
  color: #94a3b8;
}

.channel-radio {
  display: grid;
  place-items: center;
  flex: none;
  width: 18px;
  height: 18px;
  margin-left: auto;
  border: 1.5px solid #cbd5e1;
  border-radius: 50%;
  transition: border-color 0.18s ease;
}

.channel-radio i {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: #4f46e5;
  opacity: 0;
  transform: scale(0.4);
  transition: opacity 0.18s ease, transform 0.18s ease;
}

.channel-card.is-selected .channel-radio {
  border-color: #4f46e5;
}

.channel-card.is-selected .channel-radio i {
  opacity: 1;
  transform: scale(1);
}

/* ---------- 结算栏 ---------- */
.checkout-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1.25rem;
  margin-top: 1.15rem;
  padding: 1rem 1.15rem;
  border: 1px solid #e0e7ff;
  border-radius: 14px;
  background: linear-gradient(120deg, #eef2ff, #f8fafc 65%);
}

.checkout-summary {
  display: flex;
  align-items: center;
  gap: 1.35rem;
  min-width: 0;
}

.summary-item {
  display: grid;
  gap: 0.16rem;
}

.summary-item span {
  font-size: 0.7rem;
  color: #64748b;
}

.summary-amount {
  font-size: 1.3rem;
  font-weight: 800;
  color: #0f172a;
}

.summary-points {
  font-size: 1.1rem;
  font-weight: 700;
  color: #4338ca;
}

.summary-divider {
  width: 1px;
  height: 34px;
  background: #c7d2fe;
}

.summary-bonus {
  margin: 0;
  padding: 0.2rem 0.55rem;
  border-radius: 999px;
  background: #fef3c7;
  color: #b45309;
  font-size: 0.68rem;
  font-weight: 600;
  white-space: nowrap;
}

.checkout-button {
  flex: none;
  min-width: 138px;
  height: 46px;
  font-weight: 600;
}

/* ---------- 历史记录 ---------- */
.table-card {
  border-radius: 16px;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.order-code {
  padding: 0.12rem 0.35rem;
  border-radius: 5px;
  background: #f1f5f9;
  color: #334155;
  font-size: 0.74rem;
  word-break: break-all;
}

.points-text {
  font-weight: 700;
  color: #059669;
}

.money-text {
  font-weight: 600;
  color: #0f172a;
}

.time-text {
  font-size: 0.78rem;
  color: #64748b;
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 1rem;
}

/* ---------- 支付弹窗 ---------- */
.pay-body {
  display: grid;
  justify-items: center;
  gap: 0.7rem;
  padding-bottom: 0.2rem;
  text-align: center;
}

.pay-amount {
  display: flex;
  align-items: baseline;
  gap: 0.55rem;
  color: #64748b;
  font-size: 0.78rem;
}

.pay-amount strong {
  font-size: 1.55rem;
  font-weight: 800;
  color: #0f172a;
}

.qr-frame {
  display: grid;
  place-items: center;
  padding: 0.75rem;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  background: #ffffff;
  box-shadow: 0 18px 34px -30px rgba(15, 23, 42, 0.6);
}

.qr-hint {
  margin: 0.15rem 0 0;
  font-size: 0.8rem;
  color: #475569;
}

.countdown {
  margin: 0;
  padding: 0.28rem 0.7rem;
  border-radius: 999px;
  background: #eef2ff;
  color: #4338ca;
  font-size: 0.76rem;
}

.countdown strong {
  font-variant-numeric: tabular-nums;
}

.countdown.is-danger {
  background: #fef2f2;
  color: #dc2626;
}

.order-no {
  margin: 0;
  font-size: 0.7rem;
  color: #94a3b8;
  word-break: break-all;
}

.mock-pay-button {
  margin-top: 0.2rem;
  color: #94a3b8;
  font-size: 0.74rem;
}

.mock-pay-button:hover {
  color: #64748b;
}

@media (max-width: 1180px) {
  .package-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 860px) {
  .balance-banner {
    flex-direction: column;
    align-items: flex-start;
    gap: 1rem;
  }

  .balance-side {
    grid-auto-flow: column;
    align-items: center;
    justify-items: start;
  }

  .balance-meta {
    text-align: left;
  }

  .channel-grid {
    grid-template-columns: 1fr;
  }

  .checkout-bar {
    flex-direction: column;
    align-items: stretch;
  }

  .checkout-summary {
    justify-content: space-between;
  }
}

@media (max-width: 620px) {
  .package-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .checkout-summary {
    flex-wrap: wrap;
    gap: 0.8rem;
  }

  .summary-divider {
    display: none;
  }

  .checkout-button {
    width: 100%;
  }
}
</style>

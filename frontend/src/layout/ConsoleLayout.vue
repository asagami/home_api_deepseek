<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowDown,
  Cpu,
  DataAnalysis,
  DataLine,
  Expand,
  Fold,
  Goods,
  Key,
  Money,
  Monitor,
  Refresh,
  Stamp,
  SwitchButton,
  UserFilled,
  Wallet,
} from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

const COLLAPSE_STORAGE_KEY = 'console:sidebar-collapsed'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const collapsed = ref(localStorage.getItem(COLLAPSE_STORAGE_KEY) === 'true')

const pageTitle = computed(() => route.meta.title ?? '控制台')
const userInitial = computed(() => auth.user?.username.slice(0, 1).toUpperCase() ?? 'U')
const quotaText = computed(() => (auth.user ? auth.balanceQuota.toLocaleString('zh-CN') : '—'))
const isAdmin = computed(() => auth.user?.role === 'ADMIN')

watch(collapsed, (value) => {
  localStorage.setItem(COLLAPSE_STORAGE_KEY, String(value))
})

onMounted(() => {
  void auth.ensureMe().catch(() => undefined)
})

function toggleSidebar(): void {
  collapsed.value = !collapsed.value
}

function refreshProfile(): void {
  void auth
    .fetchMe()
    .then(() => ElMessage.success('额度已刷新'))
    .catch(() => ElMessage.error('获取账户信息失败'))
}

function handleUserCommand(command: string | number | object): void {
  if (command === 'logout') {
    handleLogout()
  }
}

function handleLogout(): void {
  auth.logout()
  ElMessage.success('已退出登录')
  void router.replace({ name: 'login' })
}
</script>

<template>
  <div class="console-shell" :class="{ 'is-collapsed': collapsed }">
    <aside class="sidebar">
      <div class="sidebar-head">
        <div class="brand">
          <div class="brand-mark">
            <el-icon :size="20"><Key /></el-icon>
          </div>
          <div class="brand-copy">
            <p class="brand-name">API Service</p>
            <p class="brand-sub">Developer Console</p>
          </div>
        </div>
        <button
          type="button"
          class="collapse-toggle"
          :aria-label="collapsed ? '展开侧边栏' : '折叠侧边栏'"
          :aria-expanded="!collapsed"
          :title="collapsed ? '展开侧边栏' : '折叠侧边栏'"
          @click="toggleSidebar"
        >
          <el-icon :size="16">
            <Expand v-if="collapsed" />
            <Fold v-else />
          </el-icon>
        </button>
      </div>

      <nav class="nav" aria-label="控制台导航">
        <p class="nav-label">工作台</p>
        <el-tooltip content="API Key 管理" placement="right" :disabled="!collapsed">
          <RouterLink to="/console/apikeys" class="nav-item">
            <el-icon :size="18"><Key /></el-icon>
            <span>
              <strong>API Key 管理</strong>
              <small>创建与维护密钥</small>
            </span>
          </RouterLink>
        </el-tooltip>
        <el-tooltip content="API 市场" placement="right" :disabled="!collapsed">
          <RouterLink to="/console/market" class="nav-item">
            <el-icon :size="18"><Goods /></el-icon>
            <span>
              <strong>API 市场</strong>
              <small>申请接口调用权限</small>
            </span>
          </RouterLink>
        </el-tooltip>
        <el-tooltip content="在线调试台" placement="right" :disabled="!collapsed">
          <RouterLink to="/console/playground" class="nav-item">
            <el-icon :size="18"><Monitor /></el-icon>
            <span>
              <strong>在线调试台</strong>
              <small>真实调用开放接口</small>
            </span>
          </RouterLink>
        </el-tooltip>
        <el-tooltip content="调用日志" placement="right" :disabled="!collapsed">
          <RouterLink to="/console/logs" class="nav-item">
            <el-icon :size="18"><DataLine /></el-icon>
            <span>
              <strong>调用日志</strong>
              <small>状态码与耗时分析</small>
            </span>
          </RouterLink>
        </el-tooltip>
        <el-tooltip content="充值中心" placement="right" :disabled="!collapsed">
          <RouterLink to="/console/recharge" class="nav-item">
            <el-icon :size="18"><Money /></el-icon>
            <span>
              <strong>充值中心</strong>
              <small>点数充值与扫码支付</small>
            </span>
          </RouterLink>
        </el-tooltip>

        <template v-if="isAdmin">
          <p class="nav-label is-section">管理员</p>
          <el-tooltip content="用户管理" placement="right" :disabled="!collapsed">
            <RouterLink to="/console/admin/users" class="nav-item">
              <el-icon :size="18"><UserFilled /></el-icon>
              <span>
                <strong>用户管理</strong>
                <small>封禁与额度充值</small>
              </span>
            </RouterLink>
          </el-tooltip>
          <el-tooltip content="API管理" placement="right" :disabled="!collapsed">
            <RouterLink to="/console/admin/api" class="nav-item">
              <el-icon :size="18"><Stamp /></el-icon>
              <span>
                <strong>API管理</strong>
                <small>权限工单与发布管理</small>
              </span>
            </RouterLink>
          </el-tooltip>
          <el-tooltip content="全站日志" placement="right" :disabled="!collapsed">
            <RouterLink to="/console/admin/logs" class="nav-item">
              <el-icon :size="18"><DataAnalysis /></el-icon>
              <span>
                <strong>全站日志</strong>
                <small>大盘与调用明细</small>
              </span>
            </RouterLink>
          </el-tooltip>
          <el-tooltip content="接口生成器" placement="right" :disabled="!collapsed">
            <RouterLink to="/console/admin/generator" class="nav-item">
              <el-icon :size="18"><Cpu /></el-icon>
              <span>
                <strong>接口生成器</strong>
                <small>脚手架与代码生成</small>
              </span>
            </RouterLink>
          </el-tooltip>
        </template>
      </nav>
    </aside>

    <div class="main-column">
      <header class="topbar">
        <div>
          <p class="crumb">Console</p>
          <h1 class="page-title">{{ pageTitle }}</h1>
        </div>
        <div class="topbar-actions">
          <div class="quota-card">
            <el-icon class="quota-icon"><Wallet /></el-icon>
            <div class="quota-copy">
              <span>剩余额度</span>
              <strong>{{ quotaText }}<small> 次</small></strong>
            </div>
            <button type="button" class="refresh-button" title="刷新额度" @click="refreshProfile">
              <el-icon><Refresh /></el-icon>
            </button>
          </div>

          <div class="topbar-user">
            <el-dropdown
              trigger="click"
              placement="bottom-end"
              popper-class="user-menu-popper"
              @command="handleUserCommand"
            >
              <button type="button" class="user-trigger" aria-label="账户菜单">
                <span class="avatar">{{ userInitial }}</span>
                <span class="username">{{ auth.user?.username ?? '加载中…' }}</span>
                <el-icon class="user-chevron" :size="14"><ArrowDown /></el-icon>
              </button>
              <template #dropdown>
                <el-dropdown-menu>
                  <div class="user-menu-head">
                    <span class="user-menu-name">{{ auth.user?.username ?? '未登录' }}</span>
                    <el-tag size="small" effect="plain" round>{{ auth.user?.role ?? 'USER' }}</el-tag>
                  </div>
                  <el-dropdown-item command="logout" :icon="SwitchButton" divided>退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </header>

      <main class="content">
        <RouterView v-slot="{ Component }">
          <Transition name="page" mode="out-in">
            <component :is="Component" />
          </Transition>
        </RouterView>
      </main>
    </div>
  </div>
</template>

<style scoped>
.console-shell {
  --sidebar-width: 264px;
  min-height: 100vh;
  display: grid;
  grid-template-columns: var(--sidebar-width) minmax(0, 1fr);
  background: #f5f7fb;
  transition: grid-template-columns 0.22s ease;
}

.console-shell.is-collapsed {
  --sidebar-width: 78px;
}

.sidebar {
  position: sticky;
  top: 0;
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
  height: 100vh;
  padding: 1.25rem 1rem;
  overflow-y: auto;
  overflow-x: hidden;
  color: #cbd5e1;
  background:
    radial-gradient(circle at 10% 0%, rgba(99, 102, 241, 0.28), transparent 38%),
    #0b1222;
  border-right: 1px solid rgba(148, 163, 184, 0.08);
  transition: padding 0.22s ease, gap 0.22s ease;
}

.sidebar::-webkit-scrollbar {
  width: 4px;
}

.sidebar::-webkit-scrollbar-thumb {
  background: rgba(148, 163, 184, 0.2);
  border-radius: 4px;
}

.sidebar-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}

.brand {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  min-width: 0;
  padding: 0 0.4rem;
}

.brand-mark {
  display: grid;
  place-items: center;
  flex: none;
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: linear-gradient(135deg, #6366f1, #38bdf8);
  color: #0b1222;
}

.brand-copy {
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
}

.brand-name {
  margin: 0;
  font-size: 0.98rem;
  font-weight: 700;
  color: #f8fafc;
}

.brand-sub {
  margin: 0.1rem 0 0;
  font-size: 0.7rem;
  letter-spacing: 0.08em;
  color: #64748b;
}

.collapse-toggle {
  display: grid;
  place-items: center;
  flex: none;
  width: 30px;
  height: 30px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: 8px;
  background: rgba(148, 163, 184, 0.06);
  color: #94a3b8;
  cursor: pointer;
  transition: all 0.18s ease;
}

.collapse-toggle:hover {
  border-color: rgba(129, 140, 248, 0.4);
  background: rgba(99, 102, 241, 0.16);
  color: #e2e8f0;
}

.nav {
  display: grid;
  gap: 0.35rem;
}

.nav-label {
  margin: 0 0 0.4rem 0.65rem;
  font-size: 0.66rem;
  letter-spacing: 0.18em;
  color: #475569;
  white-space: nowrap;
}

.nav-label.is-section {
  margin-top: 1rem;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.72rem 0.8rem;
  border-radius: 10px;
  color: #94a3b8;
  text-decoration: none;
  transition: background 0.18s ease, color 0.18s ease;
}

.nav-item span {
  display: grid;
  gap: 0.12rem;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
}

.nav-item strong {
  font-size: 0.86rem;
  font-weight: 600;
}

.nav-item small {
  font-size: 0.68rem;
  color: #64748b;
}

.nav-item:hover {
  background: rgba(148, 163, 184, 0.08);
  color: #e2e8f0;
}

.nav-item.router-link-active {
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.24), rgba(56, 189, 248, 0.12));
  color: #f8fafc;
  box-shadow: inset 0 0 0 1px rgba(129, 140, 248, 0.28);
}

.nav-item.router-link-active small {
  color: #a5b4fc;
}

.console-shell.is-collapsed .sidebar {
  gap: 1.2rem;
  padding: 1.25rem 0.75rem;
}

.console-shell.is-collapsed .sidebar-head {
  flex-direction: column;
  gap: 0.75rem;
}

.console-shell.is-collapsed .brand {
  justify-content: center;
  padding: 0;
}

.console-shell.is-collapsed .brand-copy,
.console-shell.is-collapsed .nav-label,
.console-shell.is-collapsed .nav-item span {
  display: none;
}

.console-shell.is-collapsed .nav-item {
  justify-content: center;
  padding: 0.68rem 0;
}

.main-column {
  min-width: 0;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 1.35rem clamp(1.25rem, 3vw, 2.5rem);
  border-bottom: 1px solid #e2e8f0;
  background: rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(10px);
}

.crumb {
  margin: 0 0 0.2rem;
  font-size: 0.68rem;
  letter-spacing: 0.16em;
  color: #94a3b8;
}

.page-title {
  margin: 0;
  font-size: 1.25rem;
  font-weight: 700;
  color: #0f172a;
}

.topbar-actions {
  display: flex;
  align-items: center;
  gap: 0.9rem;
}

.quota-card {
  display: flex;
  align-items: center;
  gap: 0.65rem;
  height: 48px;
  padding: 0 0.75rem;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #ffffff;
}

.quota-icon {
  color: #4f46e5;
}

.quota-copy {
  display: grid;
  gap: 0.1rem;
}

.quota-copy span {
  font-size: 0.66rem;
  color: #64748b;
}

.quota-copy strong {
  font-size: 0.92rem;
  color: #0f172a;
}

.quota-copy small {
  font-size: 0.68rem;
  font-weight: 400;
  color: #94a3b8;
}

.refresh-button {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border: 0;
  border-radius: 7px;
  background: #f1f5f9;
  color: #64748b;
  cursor: pointer;
}

.refresh-button:hover {
  background: #e0e7ff;
  color: #4338ca;
}

.topbar-user :deep(.el-dropdown) {
  display: block;
}

.user-trigger {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  height: 48px;
  padding: 0 0.6rem 0 0.35rem;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #ffffff;
  color: #0f172a;
  cursor: pointer;
  transition: border-color 0.18s ease, background 0.18s ease, box-shadow 0.18s ease;
}

.user-trigger:hover,
.user-trigger:focus-visible {
  border-color: #c7d2fe;
  background: #f8faff;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.06);
  outline: none;
}

.avatar {
  display: grid;
  place-items: center;
  flex: none;
  width: 30px;
  height: 30px;
  border-radius: 9px;
  background: linear-gradient(135deg, #6366f1, #38bdf8);
  color: #ffffff;
  font-size: 0.8rem;
  font-weight: 700;
}

.username {
  max-width: 132px;
  overflow: hidden;
  font-size: 0.82rem;
  font-weight: 600;
  color: #0f172a;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-chevron {
  flex: none;
  color: #94a3b8;
}

.user-menu-head {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.35rem 0.7rem 0.55rem;
}

.user-menu-name {
  max-width: 130px;
  overflow: hidden;
  font-size: 0.8rem;
  font-weight: 600;
  color: #334155;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-menu-head :deep(.el-tag) {
  height: 18px;
  padding: 0 0.4rem;
  font-size: 0.62rem;
}

:global(.user-menu-popper) {
  min-width: 190px;
}

.content {
  padding: clamp(1.25rem, 3vw, 2.5rem);
}

@media (max-width: 900px) {
  .console-shell,
  .console-shell.is-collapsed {
    grid-template-columns: 1fr;
    transition: none;
  }

  .sidebar,
  .console-shell.is-collapsed .sidebar {
    position: static;
    height: auto;
    flex-direction: row;
    align-items: center;
    gap: 0.85rem;
    padding: 0.7rem 1rem;
  }

  .sidebar-head,
  .console-shell.is-collapsed .sidebar-head {
    flex-direction: row;
    gap: 0;
  }

  .collapse-toggle {
    display: none;
  }

  .brand,
  .console-shell.is-collapsed .brand {
    justify-content: flex-start;
    padding: 0;
  }

  .brand-copy,
  .nav-label,
  .nav-item span,
  .username {
    display: none;
  }

  .nav {
    display: flex;
    gap: 0.35rem;
    margin-left: auto;
  }

  .nav-item,
  .console-shell.is-collapsed .nav-item {
    justify-content: center;
    padding: 0.5rem 0.65rem;
  }

  .topbar {
    padding: 1rem 1.25rem;
  }

  .quota-card {
    padding: 0.4rem 0.6rem;
  }
}

@media (max-width: 640px) {
  .topbar {
    flex-wrap: wrap;
    row-gap: 0.75rem;
  }

  .topbar-actions {
    margin-left: auto;
  }
}
</style>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { fetchHealth, type HealthInfo } from '@/api/health'

const health = ref<HealthInfo | null>(null)
const loading = ref(false)
const errorMessage = ref('')
const checkedAt = ref<Date | null>(null)

function formatTime(timestamp: number): string {
  return new Date(timestamp).toLocaleString('zh-CN', { hour12: false })
}

async function loadHealth(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    health.value = await fetchHealth()
    checkedAt.value = new Date()
  } catch (error: unknown) {
    health.value = null
    errorMessage.value = error instanceof Error ? error.message : '未知错误'
  } finally {
    loading.value = false
  }
}

onMounted(loadHealth)
</script>

<template>
  <main class="home">
    <section class="card">
      <header class="card-header">
        <p class="eyebrow">API Service Platform</p>
        <h1>前后端联调状态</h1>
        <p class="subtitle">
          页面通过 <code>GET /api/health</code> 经 Vite 代理请求后端，验证统一响应与异常处理链路。
        </p>
      </header>

      <div v-if="loading" class="state state-loading">
        <span class="spinner" aria-hidden="true"></span>
        正在请求 <code>/api/health</code>…
      </div>

      <div v-else-if="errorMessage" class="state state-error" role="alert">
        <p class="state-title">健康检查失败</p>
        <code class="error-message">{{ errorMessage }}</code>
        <p class="hint">请确认后端已在 8080 端口启动。</p>
      </div>

      <dl v-else-if="health" class="health-grid">
        <div class="health-item">
          <dt>服务状态</dt>
          <dd>
            <span class="badge" :class="health.status === 'UP' ? 'badge-up' : 'badge-down'">
              {{ health.status }}
            </span>
          </dd>
        </div>
        <div class="health-item">
          <dt>服务名称</dt>
          <dd>{{ health.service }}</dd>
        </div>
        <div class="health-item">
          <dt>应用版本</dt>
          <dd>{{ health.version }}</dd>
        </div>
        <div class="health-item">
          <dt>服务端时间</dt>
          <dd>{{ formatTime(health.timestamp) }}</dd>
        </div>
      </dl>

      <footer class="card-footer">
        <button type="button" class="refresh" :disabled="loading" @click="loadHealth">
          {{ loading ? '检查中…' : '重新检查' }}
        </button>
        <span v-if="checkedAt" class="checked-at">
          最近检查：{{ checkedAt.toLocaleTimeString('zh-CN', { hour12: false }) }}
        </span>
      </footer>
    </section>
  </main>
</template>

<style scoped>
.home {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 2rem;
  background:
    radial-gradient(circle at 15% 20%, rgba(59, 130, 246, 0.18), transparent 45%),
    radial-gradient(circle at 85% 80%, rgba(16, 185, 129, 0.15), transparent 40%),
    #0f172a;
  color: #e2e8f0;
}

.card {
  width: min(640px, 100%);
  padding: 2.5rem;
  border: 1px solid rgba(148, 163, 184, 0.2);
  border-radius: 1.25rem;
  background: rgba(15, 23, 42, 0.78);
  box-shadow: 0 24px 60px rgba(2, 6, 23, 0.55);
  backdrop-filter: blur(12px);
}

.eyebrow {
  margin: 0 0 0.5rem;
  font-size: 0.75rem;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: #38bdf8;
}

h1 {
  margin: 0;
  font-size: 1.75rem;
  color: #f8fafc;
}

.subtitle {
  margin: 0.75rem 0 0;
  font-size: 0.9rem;
  line-height: 1.6;
  color: #94a3b8;
}

code {
  padding: 0.1rem 0.35rem;
  border-radius: 0.3rem;
  background: rgba(56, 189, 248, 0.12);
  color: #7dd3fc;
  font-size: 0.85em;
}

.health-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 0.75rem;
  margin: 2rem 0;
}

.health-item {
  padding: 1rem 1.1rem;
  border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: 0.75rem;
  background: rgba(30, 41, 59, 0.6);
}

.health-item dt {
  margin-bottom: 0.4rem;
  font-size: 0.75rem;
  letter-spacing: 0.05em;
  color: #94a3b8;
}

.health-item dd {
  margin: 0;
  font-size: 1rem;
  font-weight: 600;
  color: #f1f5f9;
  word-break: break-all;
}

.badge {
  display: inline-block;
  padding: 0.2rem 0.6rem;
  border-radius: 999px;
  font-size: 0.8rem;
  letter-spacing: 0.05em;
}

.badge-up {
  background: rgba(16, 185, 129, 0.16);
  color: #34d399;
}

.badge-down {
  background: rgba(239, 68, 68, 0.16);
  color: #f87171;
}

.state {
  margin: 2rem 0;
  padding: 1.25rem;
  border-radius: 0.75rem;
  font-size: 0.95rem;
}

.state-loading {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  background: rgba(30, 41, 59, 0.6);
  color: #cbd5e1;
}

.spinner {
  width: 1rem;
  height: 1rem;
  border: 2px solid rgba(148, 163, 184, 0.3);
  border-top-color: #38bdf8;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.state-error {
  background: rgba(127, 29, 29, 0.25);
  border: 1px solid rgba(248, 113, 113, 0.3);
}

.state-title {
  margin: 0 0 0.5rem;
  font-weight: 600;
  color: #fca5a5;
}

.error-message {
  display: block;
  background: rgba(15, 23, 42, 0.6);
  color: #fecaca;
}

.hint {
  margin: 0.75rem 0 0;
  font-size: 0.85rem;
  color: #94a3b8;
}

.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.refresh {
  padding: 0.6rem 1.3rem;
  border: 0;
  border-radius: 0.5rem;
  background: linear-gradient(135deg, #38bdf8, #6366f1);
  color: #0f172a;
  font-size: 0.9rem;
  font-weight: 600;
  cursor: pointer;
  transition: opacity 0.2s ease;
}

.refresh:hover:not(:disabled) {
  opacity: 0.9;
}

.refresh:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.checked-at {
  font-size: 0.8rem;
  color: #64748b;
}
</style>

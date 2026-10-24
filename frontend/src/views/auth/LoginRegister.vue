<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { ArrowRight, Key, Lock, Message, User } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { sendEmailCode } from '@/api/auth'
import { useCountdown } from '@/composables/useCountdown'
import { toErrorMessage } from '@/utils/error'

type Mode = 'login' | 'register'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const mode = ref<Mode>('login')
const submitting = ref(false)
const errorMessage = ref('')
const loginFormRef = ref<FormInstance>()
const registerFormRef = ref<FormInstance>()

const loginForm = reactive({
  username: '',
  password: '',
})

const registerForm = reactive({
  username: '',
  email: '',
  code: '',
  password: '',
  confirmPassword: '',
})

const sendingCode = ref(false)
const {
  running: codeCountdownRunning,
  label: codeButtonLabel,
  start: startCodeCountdown,
} = useCountdown(60)

const loginRules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

const registerRules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 64, message: '用户名长度需在 3-64 之间', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_]+$/, message: '只能包含字母、数字和下划线', trigger: 'blur' },
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: ['blur', 'change'] },
  ],
  code: [
    { required: true, message: '请输入邮箱验证码', trigger: 'blur' },
    { pattern: /^\d{6}$/, message: '验证码为 6 位数字', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, max: 64, message: '密码长度需在 8-64 之间', trigger: 'blur' },
  ],
  confirmPassword: [{ required: true, message: '请再次输入密码', trigger: 'blur' }],
}

function switchMode(next: Mode): void {
  mode.value = next
  errorMessage.value = ''
  loginFormRef.value?.clearValidate()
  registerFormRef.value?.clearValidate()
}

async function redirectAfterAuth(): Promise<void> {
  const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
  const target = redirect.startsWith('/') ? redirect : '/console/apikeys'
  await router.replace(target)
}

async function handleLogin(): Promise<void> {
  errorMessage.value = ''
  if (!loginFormRef.value) {
    return
  }
  const valid = await loginFormRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }

  submitting.value = true
  try {
    await auth.login({
      username: loginForm.username.trim(),
      password: loginForm.password,
    })
    ElMessage.success('登录成功')
    await redirectAfterAuth()
  } catch (error) {
    errorMessage.value = toErrorMessage(error)
  } finally {
    submitting.value = false
  }
}

async function handleSendCode(): Promise<void> {
  errorMessage.value = ''
  if (!registerFormRef.value || codeCountdownRunning.value || sendingCode.value) {
    return
  }
  const valid = await registerFormRef.value.validateField('email').catch(() => false)
  if (!valid) {
    return
  }

  sendingCode.value = true
  try {
    await sendEmailCode(registerForm.email.trim())
    startCodeCountdown()
    ElMessage.success('验证码已发送，请查收邮箱')
  } catch (error) {
    errorMessage.value = toErrorMessage(error)
  } finally {
    sendingCode.value = false
  }
}

async function handleRegister(): Promise<void> {
  errorMessage.value = ''
  if (!registerFormRef.value) {
    return
  }
  const valid = await registerFormRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }
  if (registerForm.password !== registerForm.confirmPassword) {
    errorMessage.value = '两次输入的密码不一致'
    return
  }

  submitting.value = true
  try {
    await auth.register({
      username: registerForm.username.trim(),
      email: registerForm.email.trim(),
      code: registerForm.code.trim(),
      password: registerForm.password,
    })
    ElMessage.success('注册成功，已赠送 1000 次免费额度')
    await redirectAfterAuth()
  } catch (error) {
    errorMessage.value = toErrorMessage(error)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="auth-shell">
    <section class="brand-panel">
      <div class="brand-top">
        <div class="brand-mark">
          <el-icon :size="22"><Key /></el-icon>
        </div>
        <div>
          <p class="brand-name">API Service</p>
          <p class="brand-sub">开放接口管理平台</p>
        </div>
      </div>

      <div class="brand-copy">
        <p class="eyebrow">DEVELOPER CONSOLE</p>
        <h1>一次接入，<br />把接口能力交给你的产品。</h1>
        <p class="brand-desc">
          创建密钥、查看额度、在线调试。所有对外接口统一使用 API Key 鉴权，
          调用计费与访问日志自动完成。
        </p>

        <ul class="feature-list">
          <li>
            <span class="dot"></span>
            初始赠送 1000 次免费调用额度
          </li>
          <li>
            <span class="dot"></span>
            单 Key QPS 限流与账户额度双重保护
          </li>
          <li>
            <span class="dot"></span>
            真实请求调试台，自动生成 cURL / Python 代码
          </li>
        </ul>
      </div>

      <div class="code-card">
        <p class="code-title">QUICK START</p>
        <pre><code>curl -X GET \
  'http://localhost:8080/api/v1/services/echo?message=hello' \
  -H 'X-API-Key: sk-your-key'</code></pre>
      </div>
    </section>

    <section class="form-panel">
      <div class="form-card">
        <div class="mode-switch" role="tablist" aria-label="登录或注册">
          <button
            type="button"
            :class="['mode-button', { active: mode === 'login' }]"
            role="tab"
            :aria-selected="mode === 'login'"
            @click="switchMode('login')"
          >
            登录
          </button>
          <button
            type="button"
            :class="['mode-button', { active: mode === 'register' }]"
            role="tab"
            :aria-selected="mode === 'register'"
            @click="switchMode('register')"
          >
            注册
          </button>
        </div>

        <h2 class="form-title">{{ mode === 'login' ? '欢迎回来' : '创建开发者账号' }}</h2>
        <p class="form-subtitle">
          {{
            mode === 'login'
              ? '登录后管理 API Key、查看额度并在线调试接口。'
              : '注册即赠送 1000 次免费额度，无需绑定支付方式。'
          }}
        </p>

        <el-alert
          v-if="errorMessage"
          :title="errorMessage"
          type="error"
          :closable="false"
          show-icon
          class="form-alert"
        />

        <el-form
          v-show="mode === 'login'"
          ref="loginFormRef"
          :model="loginForm"
          :rules="loginRules"
          label-position="top"
          size="large"
          @submit.prevent="handleLogin"
        >
          <el-form-item label="用户名" prop="username">
            <el-input
              v-model="loginForm.username"
              :prefix-icon="User"
              placeholder="请输入用户名"
              autocomplete="username"
              clearable
            />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input
              v-model="loginForm.password"
              :prefix-icon="Lock"
              type="password"
              show-password
              placeholder="请输入密码"
              autocomplete="current-password"
              @keyup.enter="handleLogin"
            />
          </el-form-item>
          <el-button
            class="submit-button"
            type="primary"
            size="large"
            :loading="submitting"
            @click="handleLogin"
          >
            登录控制台
            <el-icon class="ml-1"><ArrowRight /></el-icon>
          </el-button>
        </el-form>

        <el-form
          v-show="mode === 'register'"
          ref="registerFormRef"
          :model="registerForm"
          :rules="registerRules"
          label-position="top"
          size="large"
          @submit.prevent="handleRegister"
        >
          <el-form-item label="用户名" prop="username">
            <el-input
              v-model="registerForm.username"
              :prefix-icon="User"
              placeholder="3-64 位字母、数字或下划线"
              autocomplete="username"
              clearable
            />
          </el-form-item>
          <el-form-item label="邮箱" prop="email">
            <el-input
              v-model="registerForm.email"
              :prefix-icon="Message"
              placeholder="用于接收注册验证码"
              autocomplete="email"
              clearable
            />
          </el-form-item>
          <el-form-item label="邮箱验证码" prop="code">
            <div class="code-row">
              <el-input
                v-model="registerForm.code"
                maxlength="6"
                placeholder="请输入 6 位验证码"
                autocomplete="one-time-code"
                inputmode="numeric"
              />
              <el-button
                class="code-button"
                :disabled="codeCountdownRunning || sendingCode"
                :loading="sendingCode"
                @click="handleSendCode"
              >
                {{ codeButtonLabel }}
              </el-button>
            </div>
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input
              v-model="registerForm.password"
              :prefix-icon="Lock"
              type="password"
              show-password
              placeholder="至少 8 位"
              autocomplete="new-password"
            />
          </el-form-item>
          <el-form-item label="确认密码" prop="confirmPassword">
            <el-input
              v-model="registerForm.confirmPassword"
              :prefix-icon="Lock"
              type="password"
              show-password
              placeholder="请再次输入密码"
              autocomplete="new-password"
              @keyup.enter="handleRegister"
            />
          </el-form-item>
          <el-button
            class="submit-button"
            type="primary"
            size="large"
            :loading="submitting"
            @click="handleRegister"
          >
            注册并进入控制台
            <el-icon class="ml-1"><ArrowRight /></el-icon>
          </el-button>
        </el-form>

        <div class="form-footer">
          <RouterLink to="/health" class="footer-link">查看服务状态</RouterLink>
          <span class="footer-hint">JWT 有效期 24 小时</span>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.auth-shell {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(0, 1.08fr) minmax(0, 0.92fr);
  background: #f5f7fb;
}

.brand-panel {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 2.5rem;
  padding: clamp(2rem, 5vw, 4.5rem);
  color: #e2e8f0;
  background:
    radial-gradient(circle at 18% 12%, rgba(99, 102, 241, 0.35), transparent 42%),
    radial-gradient(circle at 82% 78%, rgba(14, 165, 233, 0.22), transparent 45%),
    #0b1222;
  overflow: hidden;
}

.brand-panel::after {
  content: '';
  position: absolute;
  inset: 0;
  background-image: linear-gradient(rgba(148, 163, 184, 0.06) 1px, transparent 1px),
    linear-gradient(90deg, rgba(148, 163, 184, 0.06) 1px, transparent 1px);
  background-size: 48px 48px;
  mask-image: radial-gradient(circle at 40% 30%, black, transparent 72%);
  pointer-events: none;
}

.brand-top,
.brand-copy,
.code-card {
  position: relative;
  z-index: 1;
}

.brand-top {
  display: flex;
  align-items: center;
  gap: 0.85rem;
}

.brand-mark {
  display: grid;
  place-items: center;
  width: 46px;
  height: 46px;
  border-radius: 14px;
  background: linear-gradient(135deg, #6366f1, #38bdf8);
  color: #0b1222;
  box-shadow: 0 12px 30px rgba(56, 189, 248, 0.28);
}

.brand-name {
  margin: 0;
  font-size: 1.05rem;
  font-weight: 700;
  color: #f8fafc;
}

.brand-sub {
  margin: 0.15rem 0 0;
  font-size: 0.8rem;
  color: #94a3b8;
}

.eyebrow {
  margin: 0 0 1rem;
  font-size: 0.72rem;
  letter-spacing: 0.22em;
  color: #818cf8;
}

.brand-copy h1 {
  margin: 0;
  font-size: clamp(2rem, 4vw, 3.15rem);
  line-height: 1.18;
  font-weight: 700;
  color: #f8fafc;
}

.brand-desc {
  max-width: 34rem;
  margin: 1.25rem 0 0;
  font-size: 0.95rem;
  line-height: 1.75;
  color: #94a3b8;
}

.feature-list {
  display: grid;
  gap: 0.75rem;
  margin: 1.75rem 0 0;
  padding: 0;
  list-style: none;
  font-size: 0.9rem;
  color: #cbd5e1;
}

.feature-list li {
  display: flex;
  align-items: center;
  gap: 0.65rem;
}

.dot {
  width: 7px;
  height: 7px;
  border-radius: 999px;
  background: #38bdf8;
  box-shadow: 0 0 0 4px rgba(56, 189, 248, 0.12);
}

.code-card {
  max-width: 40rem;
  padding: 1.1rem 1.35rem;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 14px;
  background: rgba(15, 23, 42, 0.72);
  backdrop-filter: blur(10px);
}

.code-title {
  margin: 0 0 0.65rem;
  font-size: 0.68rem;
  letter-spacing: 0.18em;
  color: #64748b;
}

.code-card pre {
  margin: 0;
  overflow-x: auto;
  font-size: 0.78rem;
  line-height: 1.7;
  color: #7dd3fc;
}

.form-panel {
  display: grid;
  place-items: center;
  padding: clamp(1.5rem, 4vw, 3rem);
}

.form-card {
  width: min(440px, 100%);
  padding: clamp(1.5rem, 3vw, 2.5rem);
  border: 1px solid #e2e8f0;
  border-radius: 20px;
  background: #ffffff;
  box-shadow: 0 24px 60px rgba(15, 23, 42, 0.08);
}

.mode-switch {
  display: inline-grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 0.25rem;
  padding: 0.25rem;
  border-radius: 10px;
  background: #f1f5f9;
}

.mode-button {
  padding: 0.5rem 1.35rem;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #64748b;
  font-size: 0.88rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.18s ease;
}

.mode-button.active {
  background: #ffffff;
  color: #4338ca;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.08);
}

.form-title {
  margin: 1.5rem 0 0.4rem;
  font-size: 1.5rem;
  font-weight: 700;
  color: #0f172a;
}

.form-subtitle {
  margin: 0 0 1.5rem;
  font-size: 0.85rem;
  line-height: 1.65;
  color: #64748b;
}

.form-alert {
  margin-bottom: 1rem;
}

.code-row {
  display: flex;
  gap: 0.6rem;
  width: 100%;
}

.code-row .el-input {
  flex: 1;
}

.code-button {
  flex: 0 0 auto;
  min-width: 7.5rem;
}

.submit-button {
  width: 100%;
  margin-top: 0.35rem;
}

.form-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 1.5rem;
  font-size: 0.78rem;
}

.footer-link {
  color: #4f46e5;
  text-decoration: none;
}

.footer-link:hover {
  text-decoration: underline;
}

.footer-hint {
  color: #94a3b8;
}

@media (max-width: 960px) {
  .auth-shell {
    grid-template-columns: 1fr;
  }

  .brand-panel {
    display: none;
  }
}
</style>

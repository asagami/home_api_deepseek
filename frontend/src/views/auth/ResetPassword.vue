<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { CircleCheck, Lock, WarningFilled } from '@element-plus/icons-vue'
import { resetPassword, verifyResetToken } from '@/api/auth'
import { ApiError } from '@/utils/request'
import { clearToken } from '@/utils/auth'
import { toErrorMessage } from '@/utils/error'

// confirm 阶段只展示确认按钮：邮件安全网关会预取邮件里的链接，
// 若打开页面就自动换票，令牌会在用户真正点击前被消耗掉。
type Phase = 'confirm' | 'invalid' | 'form' | 'success'

const route = useRoute()
const router = useRouter()

const phase = ref<Phase>('confirm')
const invalidMessage = ref('重置链接无效或已过期，请重新获取重置邮件。')
const maskedEmail = ref('')
const verifying = ref(false)
// Reset Ticket 只保存在页面内存中，不落 localStorage，刷新即失效
const ticket = ref('')
const token = ref('')
const submitting = ref(false)
const errorMessage = ref('')
const formRef = ref<FormInstance>()
const form = reactive({
  password: '',
  confirmPassword: '',
})

const rules: FormRules = {
  password: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, max: 64, message: '密码长度需在 8-64 之间', trigger: 'blur' },
  ],
  confirmPassword: [{ required: true, message: '请再次输入新密码', trigger: 'blur' }],
}

onMounted(() => {
  const raw = route.query.token
  const value = Array.isArray(raw) ? raw[0] : raw
  if (!value) {
    invalidMessage.value = '链接缺少重置令牌，请重新获取重置邮件。'
    phase.value = 'invalid'
    return
  }
  token.value = value
  phase.value = 'confirm'
})

async function verifyLink(): Promise<void> {
  verifying.value = true
  try {
    const result = await verifyResetToken(token.value)
    ticket.value = result.ticket
    maskedEmail.value = result.maskedEmail
    phase.value = 'form'
    // 令牌已在服务端消费，同时从地址栏移除，避免留在历史记录或 Referer 中
    await router.replace({ path: '/reset-password' })
  } catch (error) {
    invalidMessage.value = toErrorMessage(error)
    phase.value = 'invalid'
  } finally {
    verifying.value = false
  }
}

async function handleSubmit(): Promise<void> {
  errorMessage.value = ''
  if (!formRef.value) {
    return
  }
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }
  if (form.password !== form.confirmPassword) {
    errorMessage.value = '两次输入的密码不一致'
    return
  }

  submitting.value = true
  try {
    await resetPassword(ticket.value, form.password)
    // 历史会话已全部失效，清掉本地令牌，避免带着失效 JWT 进控制台
    clearToken()
    phase.value = 'success'
    ElMessage.success('密码已重置')
  } catch (error) {
    if (error instanceof ApiError && error.code === 40006) {
      invalidMessage.value = error.message
      phase.value = 'invalid'
      return
    }
    errorMessage.value = toErrorMessage(error)
  } finally {
    submitting.value = false
  }
}

function goLogin(): void {
  void router.replace('/login')
}
</script>

<template>
  <div class="reset-shell">
    <div class="reset-card">
      <div class="brand">
        <div class="brand-mark">
          <el-icon :size="20"><Lock /></el-icon>
        </div>
        <div>
          <p class="brand-name">API Service</p>
          <p class="brand-sub">重置账号密码</p>
        </div>
      </div>

      <div v-if="phase === 'confirm'" class="state">
        <el-icon class="state-icon" :size="40"><Lock /></el-icon>
        <p class="state-title">确认重置密码</p>
        <p class="state-desc">
          点击下方按钮验证此重置链接，验证通过后即可设置新密码。链接会在此步骤被使用，请勿重复打开旧邮件。
        </p>
        <el-button
          type="primary"
          size="large"
          class="state-button"
          :loading="verifying"
          @click="verifyLink"
        >
          验证重置链接
        </el-button>
      </div>

      <div v-else-if="phase === 'invalid'" class="state">
        <el-icon class="state-icon warning" :size="40"><WarningFilled /></el-icon>
        <p class="state-title">链接不可用</p>
        <p class="state-desc">{{ invalidMessage }}</p>
        <el-button type="primary" size="large" class="state-button" @click="goLogin">
          返回登录页重新获取
        </el-button>
      </div>

      <div v-else-if="phase === 'form'">
        <p class="state-desc form-intro">
          正在为
          <strong>{{ maskedEmail }}</strong>
          设置新密码。重置成功后，该账号在其他设备上的登录状态会全部失效。
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
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          size="large"
          @submit.prevent="handleSubmit"
        >
          <el-form-item label="新密码" prop="password">
            <el-input
              v-model="form.password"
              :prefix-icon="Lock"
              type="password"
              show-password
              placeholder="至少 8 位"
              autocomplete="new-password"
            />
          </el-form-item>
          <el-form-item label="确认新密码" prop="confirmPassword">
            <el-input
              v-model="form.confirmPassword"
              :prefix-icon="Lock"
              type="password"
              show-password
              placeholder="请再次输入新密码"
              autocomplete="new-password"
              @keyup.enter="handleSubmit"
            />
          </el-form-item>
          <el-button
            type="primary"
            size="large"
            class="state-button"
            :loading="submitting"
            @click="handleSubmit"
          >
            重置密码
          </el-button>
        </el-form>
      </div>

      <div v-else class="state">
        <el-icon class="state-icon success" :size="44"><CircleCheck /></el-icon>
        <p class="state-title">密码已重置</p>
        <p class="state-desc">该账号的历史登录会话已全部失效，请使用新密码重新登录。</p>
        <el-button type="primary" size="large" class="state-button" @click="goLogin">
          去登录
        </el-button>
      </div>

      <p class="footer-note">如非本人操作，请立即联系管理员并检查账号安全。</p>
    </div>
  </div>
</template>

<style scoped>
.reset-shell {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 2rem 1rem;
  background:
    radial-gradient(circle at 18% 12%, rgba(99, 102, 241, 0.22), transparent 42%),
    radial-gradient(circle at 82% 78%, rgba(14, 165, 233, 0.16), transparent 45%),
    #f5f7fb;
}

.reset-card {
  width: min(440px, 100%);
  padding: 2rem;
  border-radius: 1.25rem;
  background: #ffffff;
  box-shadow: 0 24px 60px rgba(15, 23, 42, 0.12);
  border: 1px solid rgba(148, 163, 184, 0.18);
}

.brand {
  display: flex;
  align-items: center;
  gap: 0.85rem;
  margin-bottom: 1.75rem;
}

.brand-mark {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 13px;
  background: linear-gradient(135deg, #6366f1, #38bdf8);
  color: #0b1222;
}

.brand-name {
  margin: 0;
  font-size: 1rem;
  font-weight: 700;
  color: #0f172a;
}

.brand-sub {
  margin: 0.15rem 0 0;
  font-size: 0.78rem;
  color: #94a3b8;
}

.state {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 0.75rem 0 0.25rem;
}

.state-icon.warning {
  color: #f59e0b;
}

.state-icon.success {
  color: #10b981;
}

.state-title {
  margin: 0.85rem 0 0.4rem;
  font-size: 1.05rem;
  font-weight: 700;
  color: #0f172a;
}

.state-desc {
  margin: 0 0 1.25rem;
  color: #64748b;
  font-size: 0.86rem;
  line-height: 1.65;
}

.form-intro {
  margin-bottom: 1.25rem;
  text-align: left;
}

.form-intro strong {
  color: #0f172a;
}

.state-button {
  width: 100%;
}

.form-alert {
  margin-bottom: 1rem;
}

.footer-note {
  margin: 1.5rem 0 0;
  text-align: center;
  font-size: 0.75rem;
  color: #cbd5e1;
}
</style>

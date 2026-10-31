<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Lock } from '@element-plus/icons-vue'
import PasswordStrengthMeter from '@/components/PasswordStrengthMeter.vue'
import { useAuthStore } from '@/stores/auth'
import { toErrorMessage } from '@/utils/error'
import { analyzePassword } from '@/utils/passwordStrength'

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

const auth = useAuthStore()
const formRef = ref<FormInstance>()
const submitting = ref(false)
const errorMessage = ref('')

const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

function validateNewPassword(
  _rule: unknown,
  value: string,
  callback: (error?: string | Error) => void,
): void {
  if (!value) {
    callback()
    return
  }
  const analysis = analyzePassword(value, auth.user?.username)
  if (!analysis.acceptable) {
    callback(new Error(analysis.message))
    return
  }
  callback()
}

function validateConfirmPassword(
  _rule: unknown,
  value: string,
  callback: (error?: string | Error) => void,
): void {
  if (!value) {
    callback()
    return
  }
  if (value !== form.newPassword) {
    callback(new Error('两次输入的密码不一致'))
    return
  }
  callback()
}

const rules: FormRules = {
  oldPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { validator: validateNewPassword, trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' },
  ],
}

watch(visible, (open) => {
  if (!open) {
    resetForm()
  }
})

watch(
  () => form.newPassword,
  () => {
    if (form.confirmPassword) {
      void formRef.value?.validateField('confirmPassword').catch(() => false)
    }
  },
)

function resetForm(): void {
  form.oldPassword = ''
  form.newPassword = ''
  form.confirmPassword = ''
  errorMessage.value = ''
  formRef.value?.clearValidate()
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

  submitting.value = true
  try {
    await auth.changePassword({
      oldPassword: form.oldPassword,
      newPassword: form.newPassword,
    })
    ElMessage.success('密码已修改，其他设备已退出登录')
    visible.value = false
  } catch (error) {
    errorMessage.value = toErrorMessage(error)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog
    v-model="visible"
    title="修改密码"
    width="min(440px, 92vw)"
    append-to-body
    :close-on-click-modal="false"
  >
    <p class="dialog-tip">
      修改成功后，其他设备上的登录状态会立即失效，当前设备保持在线。
    </p>
    <el-alert
      v-if="errorMessage"
      :title="errorMessage"
      type="error"
      :closable="false"
      show-icon
      class="dialog-alert"
    />
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      size="large"
      @submit.prevent="handleSubmit"
    >
      <el-form-item label="当前密码" prop="oldPassword">
        <el-input
          v-model="form.oldPassword"
          :prefix-icon="Lock"
          type="password"
          show-password
          placeholder="请输入当前密码"
          autocomplete="current-password"
        />
      </el-form-item>
      <el-form-item label="新密码" prop="newPassword">
        <el-input
          v-model="form.newPassword"
          :prefix-icon="Lock"
          type="password"
          show-password
          placeholder="至少 8 位，包含大小写字母和数字"
          autocomplete="new-password"
        />
        <PasswordStrengthMeter
          :password="form.newPassword"
          :username="auth.user?.username"
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
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">
        确认修改
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.dialog-tip {
  margin: 0 0 1rem;
  color: #64748b;
  font-size: 0.84rem;
  line-height: 1.65;
}

.dialog-alert {
  margin-bottom: 1rem;
}

:deep(.el-form-item__content) {
  flex-wrap: wrap;
}
</style>

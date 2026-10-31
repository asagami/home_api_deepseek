import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  changePassword as changePasswordApi,
  getMe,
  login as loginApi,
  register as registerApi,
  type ChangePasswordPayload,
  type LoginPayload,
  type RegisterPayload,
  type UserProfile,
} from '@/api/auth'
import { clearToken, getToken, setToken } from '@/utils/auth'
import { ApiError } from '@/utils/request'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(getToken())
  const user = ref<UserProfile | null>(null)
  const loading = ref(false)

  const isAuthenticated = computed(() => Boolean(token.value))
  const balanceQuota = computed(() => user.value?.balanceQuota ?? 0)

  function applyToken(value: string): void {
    token.value = value
    setToken(value)
  }

  function clearSession(): void {
    token.value = null
    user.value = null
    clearToken()
  }

  async function login(payload: LoginPayload): Promise<void> {
    loading.value = true
    try {
      const result = await loginApi(payload)
      applyToken(result.accessToken)
      user.value = await getMe()
    } finally {
      loading.value = false
    }
  }

  async function register(payload: RegisterPayload): Promise<void> {
    loading.value = true
    try {
      await registerApi(payload)
      const result = await loginApi({ username: payload.username, password: payload.password })
      applyToken(result.accessToken)
      user.value = await getMe()
    } finally {
      loading.value = false
    }
  }

  async function changePassword(payload: ChangePasswordPayload): Promise<void> {
    const result = await changePasswordApi(payload)
    // 服务端已递增会话版本：当前设备换用新令牌，其他设备的下一次请求会因版本不匹配被登出
    applyToken(result.accessToken)
  }

  async function fetchMe(): Promise<UserProfile | null> {
    if (!token.value) {
      return null
    }
    try {
      user.value = await getMe()
      return user.value
    } catch (error) {
      if (error instanceof ApiError && error.httpStatus === 401) {
        clearSession()
      }
      throw error
    }
  }

  async function ensureMe(): Promise<void> {
    if (token.value && !user.value) {
      await fetchMe()
    }
  }

  function logout(): void {
    clearSession()
  }

  return {
    token,
    user,
    loading,
    isAuthenticated,
    balanceQuota,
    login,
    register,
    changePassword,
    fetchMe,
    ensureMe,
    logout,
    clearSession,
  }
})

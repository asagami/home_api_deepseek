import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  getMe,
  login as loginApi,
  register as registerApi,
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
    fetchMe,
    ensureMe,
    logout,
    clearSession,
  }
})

export const TOKEN_STORAGE_KEY = 'api_console_access_token'

export function getToken(): string | null {
  try {
    return window.localStorage.getItem(TOKEN_STORAGE_KEY)
  } catch {
    return null
  }
}

export function setToken(token: string): void {
  try {
    window.localStorage.setItem(TOKEN_STORAGE_KEY, token)
  } catch {
    // localStorage 不可用时仅保留内存态，由 Pinia 持有
  }
}

export function clearToken(): void {
  try {
    window.localStorage.removeItem(TOKEN_STORAGE_KEY)
  } catch {
    // ignore
  }
}

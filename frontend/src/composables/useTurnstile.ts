import { onScopeDispose, ref, watch, type Ref } from 'vue'

/**
 * Cloudflare Turnstile 人机校验 composable。
 *
 * 站点密钥通过 Vite 环境变量 {@code VITE_TURNSTILE_SITE_KEY} 注入；
 * 未配置时（本地开发）{@link enabled} 为 false，页面不渲染 widget，
 * 请求也不携带 captchaToken，后端校验关闭时直接放行。
 *
 * Turnstile token 一次性有效，siteverify 消费后即失效，
 * 因此每次发送（无论成败）后必须调用 {@link reset} 重新挑战。
 */
export interface TurnstileHandle {
  /** 是否启用人机校验（由 VITE_TURNSTILE_SITE_KEY 决定） */
  enabled: boolean
  /** 用户完成挑战后的 token，未完成/过期时为空串 */
  token: Ref<string>
  /** 脚本或 widget 加载失败时的提示，空串表示正常 */
  error: Ref<string>
  /** 重置 widget 并清空 token，开始新一轮挑战 */
  reset: () => void
  /** 重新加载失败后的脚本与 widget（网络恢复后调用） */
  retry: () => void
}

interface TurnstileApi {
  render: (
    el: HTMLElement,
    options: {
      sitekey: string
      callback: (token: string) => void
      'expired-callback'?: () => void
      'error-callback'?: () => void
    },
  ) => string
  reset: (widgetId?: string) => void
  remove: (widgetId: string) => void
}

declare global {
  interface Window {
    turnstile?: TurnstileApi
  }
}

const SCRIPT_SRC = 'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit'
const siteKey = import.meta.env.VITE_TURNSTILE_SITE_KEY as string | undefined

let scriptLoading: Promise<TurnstileApi> | null = null

/**
 * 全局只加载一次 Turnstile 脚本，多组件复用同一份 promise。
 * 加载失败时会清空缓存并移除脚本标签，允许用户重试重新插入。
 */
function loadTurnstile(): Promise<TurnstileApi> {
  if (window.turnstile) {
    return Promise.resolve(window.turnstile)
  }
  if (scriptLoading) {
    return scriptLoading
  }
  const attempt = new Promise<TurnstileApi>((resolve, reject) => {
    const script = document.createElement('script')
    script.src = SCRIPT_SRC
    script.async = true
    script.defer = true
    script.onload = () => {
      if (window.turnstile) {
        resolve(window.turnstile)
      } else {
        script.remove()
        reject(new Error('Turnstile 脚本加载后未暴露全局对象'))
      }
    }
    script.onerror = () => {
      // 移除失败的脚本标签，避免重试时复用已损坏的节点
      script.remove()
      reject(new Error('Turnstile 脚本加载失败'))
    }
    document.head.appendChild(script)
  })
  // 不缓存 rejected 的 promise：失败后置空，retry 才能重新创建脚本标签
  scriptLoading = attempt.catch((error: unknown) => {
    scriptLoading = null
    throw error
  })
  return scriptLoading
}

/**
 * 在 {@link container} 中渲染 Turnstile widget；容器为 null（v-if 未挂载）时不渲染。
 */
export function useTurnstile(container: Ref<HTMLElement | null>): TurnstileHandle {
  const enabled = Boolean(siteKey)
  const token = ref('')
  const error = ref('')
  /** retry() 递增后触发 watch 重新执行，用于加载失败后的重试 */
  const retryTick = ref(0)
  let widgetId: string | null = null
  /** 渲染序号：丢弃重试/切换容器过程中已过期的异步渲染 */
  let renderSeq = 0

  function removeWidget(): void {
    if (widgetId !== null && window.turnstile) {
      window.turnstile.remove(widgetId)
    }
    widgetId = null
  }

  if (enabled) {
    watch(
      [container, retryTick],
      async ([el]) => {
        const seq = ++renderSeq
        if (!el) {
          removeWidget()
          return
        }
        try {
          const api = await loadTurnstile()
          // 异步等待期间容器可能已被 v-if 卸载，或用户已触发新一轮重试
          if (seq !== renderSeq || container.value !== el) {
            return
          }
          removeWidget()
          error.value = ''
          widgetId = api.render(el, {
            sitekey: siteKey!,
            callback: (value) => {
              token.value = value
            },
            'expired-callback': () => {
              token.value = ''
            },
            'error-callback': () => {
              token.value = ''
            },
          })
        } catch (loadError) {
          error.value = '人机校验加载失败，请检查网络后重试'
          console.error(loadError)
        }
      },
      { immediate: true },
    )
  }

  function reset(): void {
    token.value = ''
    if (widgetId !== null && window.turnstile) {
      window.turnstile.reset(widgetId)
    }
  }

  function retry(): void {
    if (!enabled) {
      return
    }
    error.value = ''
    retryTick.value += 1
  }

  onScopeDispose(removeWidget)

  return { enabled, token, error, reset, retry }
}

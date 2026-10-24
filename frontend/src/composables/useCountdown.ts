import { computed, onScopeDispose, ref, type ComputedRef, type Ref } from 'vue'

export interface Countdown {
  /** 剩余秒数，0 表示未在倒计时 */
  seconds: Ref<number>
  /** 是否正在倒计时 */
  running: ComputedRef<boolean>
  /** 按钮文案：倒计时中显示“倒计时 Xs” */
  label: ComputedRef<string>
  /** 开始倒计时，默认 60 秒 */
  start: (duration?: number) => void
  /** 手动停止并清理定时器 */
  stop: () => void
}

export function useCountdown(totalSeconds = 60): Countdown {
  const seconds = ref(0)
  let timer: ReturnType<typeof setInterval> | null = null

  const running = computed(() => seconds.value > 0)
  const label = computed(() => (running.value ? `倒计时 ${seconds.value}s` : '获取验证码'))

  function stop(): void {
    if (timer !== null) {
      clearInterval(timer)
      timer = null
    }
    seconds.value = 0
  }

  function start(duration = totalSeconds): void {
    stop()
    seconds.value = duration
    timer = setInterval(() => {
      seconds.value -= 1
      if (seconds.value <= 0) {
        stop()
      }
    }, 1000)
  }

  // 组件卸载（或所属 scope 销毁）时清理定时器
  onScopeDispose(stop)

  return { seconds, running, label, start, stop }
}

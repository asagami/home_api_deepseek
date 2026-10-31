<script setup lang="ts">
import { computed } from 'vue'
import { Check, Close } from '@element-plus/icons-vue'
import { analyzePassword } from '@/utils/passwordStrength'

const props = defineProps<{
  password: string
  username?: string
}>()

const analysis = computed(() => analyzePassword(props.password, props.username))

const visible = computed(() => props.password.length > 0)

const barColor = computed(() => {
  if (analysis.value.score >= 65) return '#10b981'
  if (analysis.value.score >= 45) return '#f59e0b'
  return '#ef4444'
})

const ruleItems = computed(() => [
  { label: '8-64 位', met: analysis.value.lengthOk && !analysis.value.tooLong },
  { label: '小写字母', met: analysis.value.hasLower },
  { label: '大写字母', met: analysis.value.hasUpper },
  { label: '数字', met: analysis.value.hasDigit },
])

const warning = computed(() => {
  const value = analysis.value
  if (value.tooManyBytes) return '密码编码后超过 72 字节（一个汉字约占 3 字节），请缩短'
  if (value.common) return '该密码过于常见，请更换更安全的密码'
  if (value.sequentialOrRepeated) return '密码包含过多连续或重复字符，请更换'
  if (value.containsUsername) return '密码不能包含用户名'
  return ''
})
</script>

<template>
  <div v-if="visible" class="password-meter" aria-live="polite">
    <div class="meter-head">
      <div class="meter-track">
        <div
          class="meter-fill"
          :style="{ width: `${analysis.score}%`, background: barColor }"
        ></div>
      </div>
      <span class="meter-label" :style="{ color: barColor }">{{ analysis.label }}</span>
    </div>
    <ul class="meter-rules">
      <li v-for="item in ruleItems" :key="item.label" :class="{ met: item.met }">
        <el-icon :size="12">
          <Check v-if="item.met" />
          <Close v-else />
        </el-icon>
        {{ item.label }}
      </li>
      <li class="is-hint">符号可选，可进一步提升强度</li>
    </ul>
    <p v-if="warning" class="meter-warning">{{ warning }}</p>
  </div>
</template>

<style scoped>
.password-meter {
  width: 100%;
  margin-top: 0.35rem;
}

.meter-head {
  display: flex;
  align-items: center;
  gap: 0.6rem;
}

.meter-track {
  flex: 1;
  height: 6px;
  border-radius: 999px;
  background: #e2e8f0;
  overflow: hidden;
}

.meter-fill {
  height: 100%;
  border-radius: 999px;
  transition: width 0.2s ease, background 0.2s ease;
}

.meter-label {
  flex: none;
  font-size: 0.72rem;
  font-weight: 600;
}

.meter-rules {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem 0.9rem;
  margin: 0.4rem 0 0;
  padding: 0;
  list-style: none;
  font-size: 0.72rem;
  color: #94a3b8;
}

.meter-rules li {
  display: inline-flex;
  align-items: center;
  gap: 0.2rem;
}

.meter-rules li.met {
  color: #059669;
}

.meter-rules li.is-hint {
  color: #cbd5e1;
}

.meter-warning {
  margin: 0.35rem 0 0;
  font-size: 0.72rem;
  color: #dc2626;
}
</style>

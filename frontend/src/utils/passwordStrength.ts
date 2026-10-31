/**
 * 密码强度分析与规则校验，规则与后端 {@code PasswordPolicy} 保持一致：
 * 8-64 位、同时包含大写字母/小写字母/数字，且不能是常见弱口令、包含用户名或出现连续/重复字符。
 * 前端只做即时反馈，最终以服务端校验为准。
 */

export const PASSWORD_MIN_LENGTH = 8
export const PASSWORD_MAX_LENGTH = 64
/** BCrypt 对超过 72 字节的明文直接抛异常，策略层与前端都要按 UTF-8 字节数拦截 */
export const PASSWORD_MAX_BYTES = 72

const COMMON_PASSWORDS = new Set([
  '12345678', '123456789', '1234567890', '87654321', '11111111',
  '00000000', '88888888', '66666666', '12341234', 'abcd1234',
  'abc12345', 'a1234567', 'a1b2c3d4', '1q2w3e4r', '1qaz2wsx',
  'qazwsxedc', 'zaq12wsx', 'qwer1234', 'qwerty123', 'qwertyuiop',
  'asdfghjkl', 'zxcvbnm1', 'iloveyou', 'sunshine', 'princess',
  'football', 'baseball', 'superman', 'batman', 'trustno1',
  'whatever', 'starwars', 'michael123', 'jennifer', 'hunter2',
  'freedom1', 'shadow123', 'master123', 'changeme', 'test1234',
  'guest123', 'user1234', 'login123', 'hello123', 'secret123',
])

const COMMON_WORDS = [
  'password', 'passw0rd', 'qwerty', 'asdfgh', 'zxcvbn', 'admin',
  'administrator', 'welcome', 'letmein', 'iloveyou', 'monkey',
  'dragon', 'sunshine', 'princess', 'football', 'baseball',
  'superman', 'batman', 'master', 'shadow', 'michael', 'jennifer',
  'hunter', 'freedom', 'whatever', 'starwars', 'secret', 'changeme',
  'guest', 'root', 'login', 'hello', 'test', 'abc123',
]

export interface PasswordAnalysis {
  lengthOk: boolean
  tooLong: boolean
  tooManyBytes: boolean
  hasLower: boolean
  hasUpper: boolean
  hasDigit: boolean
  hasSymbol: boolean
  containsUsername: boolean
  common: boolean
  sequentialOrRepeated: boolean
  acceptable: boolean
  /** 估算熵值（bit），用于强度条 */
  entropyBits: number
  /** 0-100 的强度百分比 */
  score: number
  label: '弱' | '中' | '强'
  /** 第一条未满足的规则，用于表单校验提示 */
  message: string
}

function hasSequentialRun(password: string): boolean {
  let run = 1
  let direction = 0
  for (let i = 1; i < password.length; i++) {
    const diff = password.charCodeAt(i) - password.charCodeAt(i - 1)
    if (diff === 1 || diff === -1) {
      if (diff === direction) {
        run += 1
      } else {
        direction = diff
        run = 2
      }
      if (run >= 4) {
        return true
      }
    } else {
      run = 1
      direction = 0
    }
  }
  return false
}

function hasRepeatedRun(password: string): boolean {
  let run = 1
  for (let i = 1; i < password.length; i++) {
    if (password[i] === password[i - 1]) {
      run += 1
      if (run >= 3) {
        return true
      }
    } else {
      run = 1
    }
  }
  return false
}

function isCommonPassword(normalized: string): boolean {
  const alnum = normalized.replace(/[^a-z0-9]/g, '')
  if (COMMON_PASSWORDS.has(normalized) || COMMON_PASSWORDS.has(alnum)) {
    return true
  }
  return COMMON_WORDS.some(
    (word) => alnum.startsWith(word) && /^\d+$/.test(alnum.slice(word.length)),
  )
}

function estimateEntropy(password: string): number {
  let charset = 0
  if (/[a-z]/.test(password)) charset += 26
  if (/[A-Z]/.test(password)) charset += 26
  if (/\d/.test(password)) charset += 10
  if (/[^A-Za-z0-9]/.test(password)) charset += 33
  if (charset === 0) return 0
  return password.length * Math.log2(charset)
}

export function analyzePassword(password: string, username?: string): PasswordAnalysis {
  const lengthOk = password.length >= PASSWORD_MIN_LENGTH
  const tooLong = password.length > PASSWORD_MAX_LENGTH
  const tooManyBytes = new TextEncoder().encode(password).length > PASSWORD_MAX_BYTES
  const hasLower = /[a-z]/.test(password)
  const hasUpper = /[A-Z]/.test(password)
  const hasDigit = /\d/.test(password)
  const hasSymbol = /[^A-Za-z0-9]/.test(password)

  const normalized = password.toLowerCase()
  const name = (username ?? '').trim().toLowerCase()
  const containsUsername = name.length >= 3 && normalized.includes(name)
  const common = isCommonPassword(normalized)
  const sequentialOrRepeated = hasSequentialRun(password) || hasRepeatedRun(password)

  let message = ''
  if (!password) {
    message = '请输入密码'
  } else if (!lengthOk || tooLong) {
    message = '密码长度需在 8-64 之间'
  } else if (tooManyBytes) {
    message = '密码编码后不能超过 72 字节（一个汉字约占 3 字节），请缩短'
  } else if (!hasLower || !hasUpper || !hasDigit) {
    message = '密码需同时包含大写字母、小写字母和数字'
  } else if (common) {
    message = '该密码过于常见，请更换更安全的密码'
  } else if (sequentialOrRepeated) {
    message = '密码包含过多连续或重复字符，请更换'
  } else if (containsUsername) {
    message = '密码不能包含用户名'
  }

  const acceptable = message === ''
  let entropyBits = estimateEntropy(password)
  if (common || sequentialOrRepeated || containsUsername) {
    entropyBits = Math.min(entropyBits, 20)
  }
  const score = Math.max(0, Math.min(100, Math.round(entropyBits)))
  const label = entropyBits >= 65 ? '强' : entropyBits >= 45 ? '中' : '弱'

  return {
    lengthOk,
    tooLong,
    tooManyBytes,
    hasLower,
    hasUpper,
    hasDigit,
    hasSymbol,
    containsUsername,
    common,
    sequentialOrRepeated,
    acceptable,
    entropyBits,
    score,
    label,
    message,
  }
}

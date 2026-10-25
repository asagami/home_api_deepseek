/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 后端接口基础路径，默认 /api（由 Vite 代理转发） */
  readonly VITE_API_BASE_URL?: string
  /** Cloudflare Turnstile 站点密钥；未配置时（本地开发）不渲染人机校验组件 */
  readonly VITE_TURNSTILE_SITE_KEY?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

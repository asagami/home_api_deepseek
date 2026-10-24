/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 后端接口基础路径，默认 /api（由 Vite 代理转发） */
  readonly VITE_API_BASE_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

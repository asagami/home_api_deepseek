import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '@/utils/auth'
import { useAuthStore } from '@/stores/auth'

declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
    requiresAdmin?: boolean
    guestOnly?: boolean
    title?: string
  }
}

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      redirect: '/console/apikeys',
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/auth/LoginRegister.vue'),
      meta: { guestOnly: true, title: '登录 / 注册' },
    },
    {
      path: '/console',
      component: () => import('@/layout/ConsoleLayout.vue'),
      meta: { requiresAuth: true },
      children: [
        {
          path: '',
          redirect: '/console/apikeys',
        },
        {
          path: 'apikeys',
          name: 'apikeys',
          component: () => import('@/views/console/apikeys/Index.vue'),
          meta: { requiresAuth: true, title: 'API Key 管理' },
        },
        {
          path: 'market',
          name: 'market',
          component: () => import('@/views/console/market/Index.vue'),
          meta: { requiresAuth: true, title: 'API 市场' },
        },
        {
          path: 'playground',
          name: 'playground',
          component: () => import('@/views/console/playground/Index.vue'),
          meta: { requiresAuth: true, title: '在线调试台' },
        },
        {
          path: 'logs',
          name: 'logs',
          component: () => import('@/views/console/logs/Index.vue'),
          meta: { requiresAuth: true, title: '调用日志' },
        },
        {
          path: 'recharge',
          name: 'recharge',
          component: () => import('@/views/console/recharge/Index.vue'),
          meta: { requiresAuth: true, title: '充值中心' },
        },
        {
          path: 'admin/users',
          name: 'admin-users',
          component: () => import('@/views/console/admin/users/Index.vue'),
          meta: { requiresAuth: true, requiresAdmin: true, title: '用户管理' },
        },
        {
          path: 'admin/audit',
          name: 'admin-audit',
          component: () => import('@/views/console/admin/audit/Index.vue'),
          meta: { requiresAuth: true, requiresAdmin: true, title: '权限审批' },
        },
        {
          path: 'admin/logs',
          name: 'admin-logs',
          component: () => import('@/views/console/admin/logs/Index.vue'),
          meta: { requiresAuth: true, requiresAdmin: true, title: '全站日志' },
        },
      ],
    },
    {
      path: '/health',
      name: 'health',
      component: () => import('@/views/HomeView.vue'),
      meta: { title: '服务健康检查' },
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/',
    },
  ],
})

router.beforeEach(async (to) => {
  const token = getToken()
  if (to.meta.requiresAuth && !token) {
    return {
      name: 'login',
      query: { redirect: to.fullPath },
    }
  }
  if (to.meta.guestOnly && token) {
    return { name: 'apikeys' }
  }
  if (to.meta.requiresAdmin) {
    const auth = useAuthStore()
    try {
      await auth.ensureMe()
    } catch {
      return {
        name: 'login',
        query: { redirect: to.fullPath },
      }
    }
    if (auth.user?.role !== 'ADMIN') {
      return { name: 'apikeys' }
    }
  }
  return true
})

export default router

import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/modules/sys/views/Login.vue'),
    meta: { public: true },
  },
  {
    path: '/',
    component: () => import('@/shared/components/Layout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/modules/sys/views/Dashboard.vue'),
        meta: { titleKey: 'route.dashboard' },
      },
      // sys 模块路由 — 动态注入或静态注册
      {
        path: 'sys/user',
        name: 'SysUser',
        component: () => import('@/modules/sys/views/user/index.vue'),
        meta: { titleKey: 'route.user', permission: 'sys:user:list' },
      },
      {
        path: 'sys/role',
        name: 'SysRole',
        component: () => import('@/modules/sys/views/role/index.vue'),
        meta: { titleKey: 'route.role', permission: 'sys:role:list' },
      },
      {
        path: 'sys/menu',
        name: 'SysMenu',
        component: () => import('@/modules/sys/views/menu/index.vue'),
        meta: { titleKey: 'route.menu', permission: 'sys:menu:list' },
      },
      {
        path: 'sys/unit',
        name: 'SysUnit',
        component: () => import('@/modules/sys/views/unit/index.vue'),
        meta: { titleKey: 'route.unit', permission: 'sys:unit:list' },
      },
      {
        path: 'sys/config',
        name: 'SysConfig',
        component: () => import('@/modules/sys/views/config/index.vue'),
        meta: { titleKey: 'route.config', permission: 'sys:config:list' },
      },
      {
        path: 'sys/app',
        name: 'SysApp',
        component: () => import('@/modules/sys/views/app/index.vue'),
        meta: { titleKey: 'route.app', permission: 'sys:openapp:list' },
      },
      {
        path: 'sys/audit',
        name: 'SysAudit',
        component: () => import('@/modules/sys/views/audit/index.vue'),
        meta: { titleKey: 'route.audit', permission: 'sys:audit:list' },
      },
      {
        path: 'sys/session',
        name: 'SysSession',
        component: () => import('@/modules/sys/views/session/index.vue'),
        meta: { titleKey: 'route.session' },
      },
      {
        path: 'sys/translation',
        component: () => import('@/modules/sys/views/translation/index.vue'),
        meta: { titleKey: 'route.translation', permission: 'sys:menu:list' },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/modules/sys/views/NotFound.vue'),
    meta: { public: true },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()

  if (to.meta.public) return true

  if (!auth.isLoggedIn) {
    return { name: 'Login' }
  }

  // 首次加载时获取用户信息
  if (auth.permissions.size === 0) {
    try {
      await auth.fetchUserInfo()
    } catch {
      auth.logout()
      return { name: 'Login' }
    }
  }

  // 校验页面级权限
  const perm = to.meta.permission as string | undefined
  if (perm && !auth.hasPermission(perm)) {
    return { name: 'Dashboard' }
  }

  return true
})

export default router

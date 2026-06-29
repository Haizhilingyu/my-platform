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
        meta: { title: '首页' },
      },
      // sys 模块路由 — 动态注入或静态注册
      {
        path: 'sys/user',
        name: 'SysUser',
        component: () => import('@/modules/sys/views/user/index.vue'),
        meta: { title: '用户管理', permission: 'sys:user:list' },
      },
      {
        path: 'sys/role',
        name: 'SysRole',
        component: () => import('@/modules/sys/views/role/index.vue'),
        meta: { title: '角色管理', permission: 'sys:role:list' },
      },
      {
        path: 'sys/menu',
        name: 'SysMenu',
        component: () => import('@/modules/sys/views/menu/index.vue'),
        meta: { title: '菜单管理', permission: 'sys:menu:list' },
      },
      {
        path: 'sys/unit',
        name: 'SysUnit',
        component: () => import('@/modules/sys/views/unit/index.vue'),
        meta: { title: '单位管理', permission: 'sys:unit:list' },
      },
      {
        path: 'sys/config',
        name: 'SysConfig',
        component: () => import('@/modules/sys/views/config/index.vue'),
        meta: { title: '系统配置', permission: 'sys:config:list' },
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

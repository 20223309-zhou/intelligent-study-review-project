import { createRouter, createWebHashHistory } from 'vue-router'
import ExamGenerator from '@/pages/ExamGenerator.vue'
import PaperList from '@/pages/PaperList.vue'
import PaperDetail from '@/pages/PaperDetail.vue'
import Login from '@/pages/Login.vue'
import Register from '@/pages/Register.vue'
import AdminLogs from '@/pages/AdminLogs.vue'
import { checkLogin } from '@/utils/auth.js'

const routes = [
  {
    path: '/',
    name: 'ExamGenerator',
    component: ExamGenerator
  },
  {
    path: '/papers',
    name: 'PaperList',
    component: PaperList
  },
  {
    path: '/paper/:id',
    name: 'PaperDetail',
    component: PaperDetail
  },
  {
    path: '/login',
    name: 'Login',
    component: Login
  },
  {
    path: '/register',
    name: 'Register',
    component: Register
  },
  {
    path: '/admin/logs',
    name: 'AdminLogs',
    component: AdminLogs,
    // 管理员专属。注意这只是"前端别让人点进来"，
    // 真正的边界在后端 @AuthCheck(mustRole = ADMIN_ROLE) 上
    meta: { requiresAdmin: true }
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

/**
 * 管理员路由守卫。
 * 只对 meta.requiresAdmin 的路由查一次当前用户：未登录、非 admin、接口出错，
 * 一律退回「我的试卷」—— 不给"提示一下再跳"的中间态，避免刷新时页面闪一下。
 * 非管理员即使手动改 hash 进来，后端的 @AuthCheck 也会把数据接口拦掉。
 */
router.beforeEach(async (to) => {
  if (!to.meta?.requiresAdmin) {
    return true
  }
  try {
    const res = await checkLogin()
    if (res?.code === 0 && res?.data?.userRole === 'admin') {
      return true
    }
  } catch {
    // 网络异常按无权限处理
  }
  return { path: '/papers', replace: true }
})

export default router

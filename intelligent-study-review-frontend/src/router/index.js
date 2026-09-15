import { createRouter, createWebHashHistory } from 'vue-router'
import ExamGenerator from '@/pages/ExamGenerator.vue'
import PaperList from '@/pages/PaperList.vue'
import PaperDetail from '@/pages/PaperDetail.vue'
import Login from '@/pages/Login.vue'
import Register from '@/pages/Register.vue'

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
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

export default router

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import { checkLogin, logoutUser } from '@/utils/auth.js'
import UiFeedback from '@/components/ui/UiFeedback.vue'
import BrandLogo from '@/components/BrandLogo.vue'

const router = useRouter()
const route = useRoute()
const currentUser = ref(null)

/** 管理员入口只对 admin 展示；非管理员就算手动输入 hash 也会被路由守卫弹回 */
const isAdmin = computed(() => currentUser.value?.userRole === 'admin')

onMounted(async () => {
  await refreshUser()
})

// 每次路由变化时刷新登录状态（解决登录后不刷新的问题）
watch(() => route.path, async () => {
  await refreshUser()
})

async function refreshUser() {
  try {
    const res = await checkLogin()
    if (res.code === 0 && res.data) {
      currentUser.value = res.data
    } else {
      currentUser.value = null
    }
  } catch {
    currentUser.value = null
  }
}

async function handleLogout() {
  try {
    await logoutUser()
  } catch {
    // ignore
  }
  currentUser.value = null
  router.push('/')
}
</script>

<template>
  <div class="app-container">
    <header class="app-header">
      <div class="header-inner">
        <div class="logo-area" @click="router.push('/')" style="cursor: pointer">
          <BrandLogo :height="48" />
        </div>

        <div class="header-right">
          <router-link to="/" class="nav-link" :class="{ active: $route.path === '/' }">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M12 20h9"/>
              <path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"/>
            </svg>
            生成试卷
          </router-link>
          <router-link to="/papers" class="nav-link" :class="{ active: $route.path === '/papers' || $route.path.startsWith('/paper/') }">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
              <polyline points="14 2 14 8 20 8"/>
              <line x1="16" y1="13" x2="8" y2="13"/>
              <line x1="16" y1="17" x2="8" y2="17"/>
            </svg>
            我的试卷
          </router-link>

          <router-link v-if="isAdmin" to="/admin/logs" class="nav-link"
            :class="{ active: $route.path.startsWith('/admin') }">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <line x1="9" y1="6" x2="21" y2="6"/>
              <line x1="9" y1="12" x2="21" y2="12"/>
              <line x1="9" y1="18" x2="21" y2="18"/>
              <circle cx="4" cy="6" r="1.2"/>
              <circle cx="4" cy="12" r="1.2"/>
              <circle cx="4" cy="18" r="1.2"/>
            </svg>
            生成日志
          </router-link>

          <a class="gh-link" href="https://github.com/20223309-zhou/intelligent-study-review-project"
            target="_blank" rel="noopener noreferrer" title="GitHub 仓库" aria-label="GitHub 仓库">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
              <path d="M12 .297c-6.63 0-12 5.373-12 12 0 5.303 3.438 9.8 8.205 11.385.6.113.82-.258.82-.577 0-.285-.01-1.04-.015-2.04-3.338.724-4.042-1.61-4.042-1.61C4.422 18.07 3.633 17.7 3.633 17.7c-1.087-.744.084-.729.084-.729 1.205.084 1.838 1.236 1.838 1.236 1.07 1.835 2.809 1.305 3.495.998.108-.776.417-1.305.76-1.605-2.665-.3-5.466-1.332-5.466-5.93 0-1.31.465-2.38 1.235-3.22-.135-.303-.54-1.523.105-3.176 0 0 1.005-.322 3.3 1.23.96-.267 1.98-.399 3-.405 1.02.006 2.04.138 3 .405 2.28-1.552 3.285-1.23 3.285-1.23.645 1.653.24 2.873.12 3.176.765.84 1.23 1.91 1.23 3.22 0 4.61-2.805 5.625-5.475 5.92.42.36.81 1.096.81 2.22 0 1.606-.015 2.896-.015 3.286 0 .315.21.69.825.57C20.565 22.092 24 17.592 24 12.297c0-6.627-5.373-12-12-12"/>
            </svg>
          </a>

          <template v-if="currentUser">
            <div class="user-menu">
              <div class="user-avatar">{{ (currentUser.userName || currentUser.userAccount || '?').charAt(0).toUpperCase() }}</div>
              <span class="user-name">{{ currentUser.userName || currentUser.userAccount }}</span>
              <button class="btn-logout" @click="handleLogout" title="退出登录">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/>
                  <polyline points="16 17 21 12 16 7"/>
                  <line x1="21" y1="12" x2="9" y2="12"/>
                </svg>
              </button>
            </div>
          </template>
          <template v-else>
            <router-link to="/login" class="nav-link btn-login">登录</router-link>
            <router-link to="/register" class="nav-link btn-register">注册</router-link>
          </template>
        </div>
      </div>
    </header>
    <main class="app-main">
      <RouterView />
    </main>

    <!-- 全局确认弹层 + 轻提示 -->
    <UiFeedback />
  </div>
</template>

<style scoped>
.app-container {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.app-header {
  /* 半透明 + 背景模糊：滚动时页面的分层背景会透出来，形成纵深 */
  background: rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border-bottom: 1px solid var(--color-border);
  padding: var(--space-3) var(--space-6);
  position: sticky;
  top: 0;
  z-index: 100;
  box-shadow: var(--shadow-sm);
}

.header-inner {
  max-width: var(--container);
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
}

.logo-area {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  /* logo 内的 fill 全部用 currentColor，换主色时无需改 SVG */
  color: var(--color-primary);
}

.header-right {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.nav-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  font-size: var(--fs-sm);
  font-weight: var(--fw-semibold);
  color: var(--color-text-secondary);
  border-radius: var(--radius-md);
  transition: all var(--transition-fast);
  text-decoration: none;
}

.nav-link:hover {
  color: var(--color-primary);
  background: var(--color-primary-bg);
}

.nav-link.active {
  color: var(--color-primary);
  background: var(--color-primary-bg);
}

/* GitHub 入口：只是外链，不参与登录态 */
.gh-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  color: var(--color-text-muted);
  transition: all var(--transition-fast);
}

.gh-link:hover {
  color: var(--color-text-primary);
  background: var(--color-bg);
}

.btn-login {
  color: var(--color-text-secondary);
}

.btn-register {
  background: var(--color-primary-gradient);
  color: white;
  box-shadow: var(--shadow-primary);
}

.btn-register:hover {
  box-shadow: var(--shadow-primary-lg);
  transform: translateY(-1px);
  color: white;
  background: var(--color-primary-gradient);
}

/* 用户菜单 */
.user-menu {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: 4px 4px 4px 10px;
  background: var(--color-primary-bg);
  border-radius: var(--radius-full);
  border: 1px solid var(--color-primary-border);
}

.user-avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: var(--color-primary-gradient);
  color: white;
  font-size: var(--fs-2xs);
  font-weight: var(--fw-bold);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.user-name {
  font-size: var(--fs-sm);
  font-weight: var(--fw-semibold);
  color: var(--color-primary-dark);
  max-width: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.btn-logout {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  color: var(--color-text-muted);
  transition: all var(--transition-fast);
}

.btn-logout:hover {
  background: var(--color-error-bg);
  color: var(--color-error);
}

.app-main {
  flex: 1;
  padding: var(--space-8) var(--space-6);
  max-width: var(--container);
  width: 100%;
  margin: 0 auto;
}

@media (max-width: 640px) {
  .app-header {
    padding: var(--space-3) var(--space-4);
  }

  .app-main {
    padding: var(--space-4) var(--space-3);
  }

  .nav-link {
    padding: 8px 10px;
    font-size: var(--fs-xs);
  }

  .nav-link svg {
    display: none;
  }

  .user-name {
    display: none;
  }
}

@media (max-width: 400px) {
  .btn-register {
    padding: 8px;
  }

  .nav-link span {
    display: none;
  }
}
</style>

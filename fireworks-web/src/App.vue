<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const router = useRouter()
const route = useRoute()

const isDark = ref(true)

function applyTheme(dark: boolean) {
  document.documentElement.classList.toggle('dark', dark)
}

function toggleTheme() {
  isDark.value = !isDark.value
  applyTheme(isDark.value)
  localStorage.setItem('theme', isDark.value ? 'dark' : 'light')
}

onMounted(() => {
  authStore.checkStatus()
  const saved = localStorage.getItem('theme')
  if (saved === 'light') {
    isDark.value = false
  }
  applyTheme(isDark.value)
})

function handleLogout() {
  authStore.logout().then(() => {
    router.push('/')
  })
}
</script>

<template>
  <div class="app-shell">
    <!-- 自定义暗色导航栏 -->
    <nav class="fw-nav">
      <div class="fw-nav-inner">
        <router-link to="/" class="fw-nav-brand">
          <span class="fw-nav-icon">🎆</span>
          Fireworks
        </router-link>

        <div class="fw-nav-links">
          <router-link
            to="/"
            class="fw-nav-link"
            :class="{ active: route.path === '/' }"
          >主页</router-link>
          <router-link
            to="/vault"
            class="fw-nav-link"
            :class="{ active: route.path === '/vault' }"
          >密码本</router-link>
          <router-link
            to="/family-trees"
            class="fw-nav-link"
            :class="{ active: route.path.startsWith('/family-trees') }"
          >族谱</router-link>
        </div>

        <div class="fw-nav-actions">
          <button
            class="fw-btn fw-btn-ghost fw-btn-sm theme-toggle"
            @click="toggleTheme"
            :title="isDark ? '切换亮色' : '切换暗色'"
          >
            {{ isDark ? '☀' : '🌙' }}
          </button>

          <template v-if="!authStore.isAuthenticated">
            <button class="fw-btn fw-btn-sm" @click="router.push('/login')">
              登录
            </button>
            <button class="fw-btn fw-btn-primary fw-btn-sm" @click="router.push('/register')">
              注册
            </button>
          </template>
          <template v-else>
            <span class="fw-nav-user mono">{{ authStore.account?.username }}</span>
            <button class="fw-btn fw-btn-sm" @click="handleLogout">登出</button>
          </template>
        </div>
      </div>
    </nav>

    <!-- 内容区域 -->
    <main class="fw-main">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

/* 导航栏 */
.fw-nav {
  position: sticky;
  top: 0;
  z-index: 100;
  height: 56px;
  border-bottom: 1px solid var(--fw-border);
  background: var(--fw-nav-bg);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
}

.fw-nav-inner {
  max-width: 1200px;
  margin: 0 auto;
  height: 100%;
  display: flex;
  align-items: center;
  padding: 0 1.5rem;
  gap: 2rem;
}

.fw-nav-brand {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 1.15rem;
  font-weight: 700;
  color: var(--fw-accent);
  white-space: nowrap;
  letter-spacing: 0.02em;
}

.fw-nav-brand:hover {
  color: var(--fw-accent-hover);
}

.fw-nav-icon {
  font-size: 1.3rem;
}

.fw-nav-links {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  flex: 1;
}

.fw-nav-link {
  padding: 0.4rem 0.85rem;
  border-radius: var(--fw-radius);
  font-size: 0.9rem;
  color: var(--fw-text-dim);
  transition: all 0.25s;
}

.fw-nav-link:hover {
  color: var(--fw-text);
  background: rgba(255, 215, 0, 0.08);
}

.fw-nav-link.active {
  color: var(--fw-accent);
  background: rgba(255, 215, 0, 0.1);
}

.fw-nav-actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.theme-toggle {
  font-size: 1.1rem;
  padding: 0.25rem 0.5rem;
}

.fw-nav-user {
  font-size: 0.85rem;
  color: var(--fw-secondary);
}

/* 内容区 */
.fw-main {
  flex: 1;
  min-height: 0;
}
</style>

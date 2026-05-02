<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { Sunny, Moon } from '@element-plus/icons-vue'

const authStore = useAuthStore()
const router = useRouter()
const route = useRoute()

const isDark = ref(false)

function applyTheme(dark: boolean) {
  isDark.value = dark
  const cl = document.documentElement.classList
  cl.remove('dark', 'light')
  cl.add(dark ? 'dark' : 'light')
  localStorage.setItem('theme', dark ? 'dark' : 'light')
}

function toggleTheme() {
  applyTheme(!isDark.value)
}

onMounted(() => {
  authStore.checkStatus()
  const saved = localStorage.getItem('theme')
  if (saved) {
    applyTheme(saved === 'dark')
  } else {
    const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches
    if (prefersDark) {
      isDark.value = true
      document.documentElement.classList.add('dark')
    } else {
      isDark.value = false
      document.documentElement.classList.add('light')
    }
  }
})

function handleLogout() {
  authStore.logout().then(() => {
    router.push('/')
  })
}
</script>

<template>
  <el-container class="app-container">
    <!-- 顶部导航栏 -->
    <el-header class="app-header">
      <div class="header-left">
        <span class="app-logo">Fireworks</span>
        <el-menu
          :default-active="route.path"
          mode="horizontal"
          :ellipsis="false"
          router
          class="header-menu"
        >
          <el-menu-item index="/">主页</el-menu-item>
          <el-menu-item index="/vault">密码本</el-menu-item>
          <el-menu-item index="/family-trees">族谱</el-menu-item>
        </el-menu>
      </div>

      <div class="header-right">
        <el-button size="small" circle @click="toggleTheme" :title="isDark ? '切换亮色' : '切换暗色'">
          <el-icon><Moon v-if="isDark" /><Sunny v-else /></el-icon>
        </el-button>
        <template v-if="!authStore.isAuthenticated">
          <el-button size="small" @click="router.push('/login')">登录</el-button>
          <el-button size="small" type="primary" @click="router.push('/register')">注册</el-button>
        </template>
        <template v-else>
          <span class="header-user">{{ authStore.account?.username }}</span>
          <el-button size="small" @click="handleLogout">登出</el-button>
        </template>
      </div>
    </el-header>

    <!-- 内容区域 -->
    <el-main class="app-main">
      <router-view />
    </el-main>
  </el-container>
</template>

<style>
/* 全局重置 — 覆盖 main.css 中不适用的样式 */
body {
  margin: 0;
  display: block;
  place-items: unset;
}

#app {
  max-width: none;
  padding: 0;
  display: block;
  grid-template-columns: unset;
}

@media (min-width: 1024px) {
  body {
    display: block;
    place-items: unset;
  }
  #app {
    display: block;
    grid-template-columns: unset;
  }
}
</style>

<style scoped>
.app-container {
  min-height: 100vh;
}

.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 1.5rem;
  border-bottom: 1px solid var(--el-border-color-light);
  height: 60px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.app-logo {
  font-size: 1.2rem;
  font-weight: 700;
  color: var(--el-color-primary);
  white-space: nowrap;
}

.header-menu {
  border-bottom: none !important;
}

.header-menu .el-menu-item {
  height: 60px;
  line-height: 60px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.header-user {
  font-size: 0.9rem;
  color: var(--el-text-color-regular);
}

.app-main {
  max-width: 960px;
  margin: 0 auto;
  padding: 1.5rem;
}
</style>

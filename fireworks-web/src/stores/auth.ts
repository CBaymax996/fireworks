import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import * as authApi from '@/api/auth'

export const useAuthStore = defineStore('auth', () => {
  const account = ref<authApi.AccountResponse | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)

  const isAuthenticated = computed(() => account.value !== null)

  async function register(username: string, password: string) {
    loading.value = true
    error.value = null
    try {
      const res = await authApi.register({ username, password })
      account.value = res
      return res
    } catch (e: any) {
      error.value = e.message || '注册失败'
      throw e
    } finally {
      loading.value = false
    }
  }

  async function login(username: string, password: string) {
    loading.value = true
    error.value = null
    try {
      const res = await authApi.login({ username, password })
      account.value = res.account
      return res
    } catch (e: any) {
      error.value = e.message || '登录失败'
      throw e
    } finally {
      loading.value = false
    }
  }

  async function logout() {
    loading.value = true
    try {
      await authApi.logout()
      account.value = null
    } catch (e: any) {
      error.value = e.message || '登出失败'
    } finally {
      loading.value = false
    }
  }

  async function checkStatus() {
    try {
      const res = await authApi.authStatus()
      if (!res.authenticated) {
        account.value = null
      }
    } catch {
      account.value = null
    }
  }

  return {
    account,
    loading,
    error,
    isAuthenticated,
    register,
    login,
    logout,
    checkStatus,
  }
})

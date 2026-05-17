import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import * as vaultApi from '@/api/vault'
import type { PasswordEntry, DerivedPasswordResponse, EntryRequest, UpdateEntryRequest } from '@/api/vault'

export const useVaultStore = defineStore('vault', () => {
  const initialized = ref(false)
  const authenticated = ref(false)
  const entries = ref<PasswordEntry[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

  // 派生密码缓存（内存中，不持久化）
  const derivedPasswords = ref<Record<number, string>>({})
  const derivedLoading = ref<Record<number, boolean>>({})

  const entryCount = computed(() => entries.value.length)

  // ---------- 状态 ----------

  async function checkStatus() {
    try {
      const res = await vaultApi.vaultStatus()
      initialized.value = res.initialized
      authenticated.value = res.authenticated
      if (res.authenticated) {
        await loadEntries()
      }
    } catch {
      initialized.value = false
      authenticated.value = false
    }
  }

  // ---------- 初始化与认证 ----------

  async function setup(masterPassword: string) {
    loading.value = true
    error.value = null
    try {
      await vaultApi.vaultSetup({ masterPassword })
      initialized.value = true
    } catch (e: any) {
      error.value = e.message || '初始化失败'
      throw e
    } finally {
      loading.value = false
    }
  }

  async function login(masterPassword: string) {
    loading.value = true
    error.value = null
    try {
      await vaultApi.vaultLogin({ masterPassword })
      authenticated.value = true
      await loadEntries()
    } catch (e: any) {
      error.value = e.message || '解锁失败'
      throw e
    } finally {
      loading.value = false
    }
  }

  async function logout() {
    loading.value = true
    try {
      await vaultApi.vaultLogout()
      authenticated.value = false
      entries.value = []
      derivedPasswords.value = {}
    } catch (e: any) {
      error.value = e.message || '锁定失败'
    } finally {
      loading.value = false
    }
  }

  // ---------- 条目 CRUD ----------

  async function loadEntries() {
    try {
      entries.value = await vaultApi.listEntries()
    } catch (e: any) {
      error.value = e.message || '加载条目失败'
    }
  }

  async function addEntry(req: EntryRequest): Promise<PasswordEntry> {
    loading.value = true
    error.value = null
    try {
      const entry = await vaultApi.createEntry(req)
      entries.value.push(entry)
      return entry
    } catch (e: any) {
      error.value = e.message || '创建条目失败'
      throw e
    } finally {
      loading.value = false
    }
  }

  async function editEntry(id: number, req: UpdateEntryRequest): Promise<PasswordEntry> {
    loading.value = true
    error.value = null
    try {
      const updated = await vaultApi.updateEntry(id, req)
      const idx = entries.value.findIndex((e) => e.id === id)
      if (idx !== -1) entries.value[idx] = updated
      return updated
    } catch (e: any) {
      error.value = e.message || '更新条目失败'
      throw e
    } finally {
      loading.value = false
    }
  }

  async function removeEntry(id: number) {
    loading.value = true
    error.value = null
    try {
      await vaultApi.deleteEntry(id)
      entries.value = entries.value.filter((e) => e.id !== id)
      delete derivedPasswords.value[id]
    } catch (e: any) {
      error.value = e.message || '删除条目失败'
      throw e
    } finally {
      loading.value = false
    }
  }

  // ---------- 轮换与派生 ----------

  async function rotate(id: number): Promise<PasswordEntry> {
    loading.value = true
    error.value = null
    try {
      const rotated = await vaultApi.rotateEntry(id)
      const idx = entries.value.findIndex((e) => e.id === id)
      if (idx !== -1) entries.value[idx] = rotated
      delete derivedPasswords.value[id]
      return rotated
    } catch (e: any) {
      error.value = e.message || '轮换失败'
      throw e
    } finally {
      loading.value = false
    }
  }

  async function derivePassword(id: number, counter?: number): Promise<DerivedPasswordResponse> {
    derivedLoading.value[id] = true
    error.value = null
    try {
      const res = await vaultApi.derivePassword(id, counter)
      derivedPasswords.value[id] = res.password
      return res
    } catch (e: any) {
      error.value = e.message || '派生密码失败'
      throw e
    } finally {
      derivedLoading.value[id] = false
    }
  }

  function getCachedPassword(id: number): string | undefined {
    return derivedPasswords.value[id]
  }

  function clearPassword(id: number) {
    delete derivedPasswords.value[id]
  }

  // ---------- 随机密码生成 ----------

  /** 调用后端生成随机密码，返回密码字符串 */
  async function generatePassword(params?: {
    length?: number
    lowercase?: boolean
    uppercase?: boolean
    digits?: boolean
    symbols?: boolean
  }): Promise<string> {
    error.value = null
    try {
      const res = await vaultApi.generatePassword(params ?? {})
      return res.password
    } catch (e: any) {
      error.value = e.message || '生成密码失败'
      throw e
    }
  }

  return {
    initialized,
    authenticated,
    entries,
    loading,
    error,
    derivedPasswords,
    derivedLoading,
    entryCount,
    checkStatus,
    setup,
    login,
    logout,
    loadEntries,
    addEntry,
    editEntry,
    removeEntry,
    rotate,
    derivePassword,
    getCachedPassword,
    clearPassword,
    generatePassword,
  }
})

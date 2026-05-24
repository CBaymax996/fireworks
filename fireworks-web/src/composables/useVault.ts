import { useLazyQuery, useMutation } from '@vue/apollo-composable'
import {
  VaultStatus as VAULT_STATUS,
  VaultSetup as VAULT_SETUP,
  VaultLogin as VAULT_LOGIN,
  VaultLogout as VAULT_LOGOUT,
  VaultEntries as VAULT_ENTRIES,
  VaultEntry as VAULT_ENTRY,
  CreateVaultEntry as CREATE_VAULT_ENTRY,
  UpdateVaultEntry as UPDATE_VAULT_ENTRY,
  DeleteVaultEntry as DELETE_VAULT_ENTRY,
  RotateVaultEntry as ROTATE_VAULT_ENTRY,
  DerivePassword as DERIVE_PASSWORD,
  GeneratePassword as GENERATE_PASSWORD,
} from '@/graphql/vault.gql'

// ── 类型定义 ──

export interface PasswordEntry {
  id: string | null
  website: string
  username: string
  notes: string
  counter: number
  length: number
  useLowercase: boolean
  useUppercase: boolean
  useDigits: boolean
  useSymbols: boolean
  password: string | null
  mode: 'DERIVED' | 'STORED'
  createdAt: string
  updatedAt: string
}

export interface DerivedPasswordResult {
  entry: PasswordEntry
  password: string
  counter: number
}

export interface VaultStatusResult {
  initialized: boolean
  authenticated: boolean
}

export interface VaultEntryInput {
  website: string
  username: string
  notes?: string
  counter?: number
  length?: number
  useLowercase?: boolean
  useUppercase?: boolean
  useDigits?: boolean
  useSymbols?: boolean
  password?: string
  mode?: 'DERIVED' | 'STORED'
}

export interface VaultEntryUpdateInput {
  website?: string
  username?: string
  notes?: string
  length?: number
  useLowercase?: boolean
  useUppercase?: boolean
  useDigits?: boolean
  useSymbols?: boolean
  password?: string
  mode?: 'DERIVED' | 'STORED'
}

// ── Composable ──

export function useVault() {
  // —— 状态查询 ——
  const { load: loadVaultStatus } = useLazyQuery<{ vaultStatus: VaultStatusResult }>(VAULT_STATUS, undefined, {
    fetchPolicy: 'network-only',
  })

  // —— 认证 mutations ——
  const { mutate: vaultSetupMutate } = useMutation(VAULT_SETUP)
  const { mutate: vaultLoginMutate } = useMutation<{ vaultLogin: boolean }>(VAULT_LOGIN)
  const { mutate: vaultLogoutMutate } = useMutation<{ vaultLogout: boolean }>(VAULT_LOGOUT)

  // —— 条目查询 ——
  const { load: loadEntries } = useLazyQuery<{ vaultEntries: PasswordEntry[] }>(VAULT_ENTRIES, undefined, {
    fetchPolicy: 'network-only',
  })
  const { load: loadEntry } = useLazyQuery<{ vaultEntry: PasswordEntry | null }>(VAULT_ENTRY)

  // —— 条目 mutations ——
  const { mutate: createEntryMutate } = useMutation<{ createVaultEntry: PasswordEntry }>(CREATE_VAULT_ENTRY)
  const { mutate: updateEntryMutate } = useMutation<{ updateVaultEntry: PasswordEntry }>(UPDATE_VAULT_ENTRY)
  const { mutate: deleteEntryMutate } = useMutation<{ deleteVaultEntry: boolean }>(DELETE_VAULT_ENTRY)
  const { mutate: rotateEntryMutate } = useMutation<{ rotateVaultEntry: PasswordEntry }>(ROTATE_VAULT_ENTRY)

  // —— 派生/生成查询 ——
  const { load: loadDerivePassword } = useLazyQuery<{ derivePassword: DerivedPasswordResult }>(DERIVE_PASSWORD, undefined, {
    fetchPolicy: 'network-only',
  })
  const { load: loadGeneratePassword } = useLazyQuery<{ generatePassword: { password: string } }>(GENERATE_PASSWORD, undefined, {
    fetchPolicy: 'network-only',
  })

  // ===== 封装方法 =====

  /** 查询 Vault 状态 */
  async function vaultStatus(): Promise<VaultStatusResult> {
    const result = await loadVaultStatus()
    return result!.vaultStatus
  }

  /** 初始化 Vault */
  async function vaultSetup(masterPassword: string): Promise<void> {
    await vaultSetupMutate({ masterPassword })
  }

  /** 解锁 Vault */
  async function vaultLogin(masterPassword: string): Promise<boolean> {
    const result = await vaultLoginMutate({ masterPassword })
    return result!.data!.vaultLogin
  }

  /** 锁定 Vault */
  async function vaultLogout(): Promise<boolean> {
    const result = await vaultLogoutMutate()
    return result!.data!.vaultLogout
  }

  /** 列出所有条目 */
  async function listEntries(): Promise<PasswordEntry[]> {
    const result = await loadEntries()
    return result!.vaultEntries
  }

  /** 获取单个条目 */
  async function getEntry(id: string): Promise<PasswordEntry | null> {
    const result = await loadEntry(undefined, { id })
    return result!.vaultEntry
  }

  /** 创建条目 */
  async function createEntry(input: VaultEntryInput): Promise<PasswordEntry> {
    const result = await createEntryMutate({ input })
    return result!.data!.createVaultEntry
  }

  /** 更新条目 */
  async function updateEntry(id: string, input: VaultEntryUpdateInput): Promise<PasswordEntry> {
    const result = await updateEntryMutate({ id, input })
    return result!.data!.updateVaultEntry
  }

  /** 删除条目 */
  async function deleteEntry(id: string): Promise<boolean> {
    const result = await deleteEntryMutate({ id })
    return result!.data!.deleteVaultEntry
  }

  /** 轮换条目密码（counter +1） */
  async function rotateEntry(id: string): Promise<PasswordEntry> {
    const result = await rotateEntryMutate({ id })
    return result!.data!.rotateVaultEntry
  }

  /** 派生密码 */
  async function derivePassword(id: string, counter?: number): Promise<DerivedPasswordResult> {
    const result = await loadDerivePassword(undefined, { id, counter })
    return result!.derivePassword
  }

  /** 生成随机密码 */
  async function generatePassword(params: {
    length?: number
    lowercase?: boolean
    uppercase?: boolean
    digits?: boolean
    symbols?: boolean
  }): Promise<string> {
    const result = await loadGeneratePassword(undefined, {
      length: params.length ?? 16,
      lowercase: params.lowercase ?? true,
      uppercase: params.uppercase ?? true,
      digits: params.digits ?? true,
      symbols: params.symbols ?? true,
    })
    return result!.generatePassword.password
  }

  return {
    vaultStatus,
    vaultSetup,
    vaultLogin,
    vaultLogout,
    listEntries,
    getEntry,
    createEntry,
    updateEntry,
    deleteEntry,
    rotateEntry,
    derivePassword,
    generatePassword,
  }
}

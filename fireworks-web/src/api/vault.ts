const API_BASE = import.meta.env.DEV ? '' : ''

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    ...options,
  })
  if (!res.ok) {
    const body = await res.json().catch(() => ({}))
    throw new Error(body.message || `HTTP ${res.status}`)
  }
  if (res.status === 204) return undefined as T
  const text = await res.text()
  if (!text) return undefined as T
  return JSON.parse(text)
}

// ---------- 类型 ----------

export interface PasswordEntry {
  id: number | null
  website: string
  username: string
  notes: string
  counter: number
  length: number
  useLowercase: boolean
  useUppercase: boolean
  useDigits: boolean
  useSymbols: boolean
  password: string | null   // 存储模式的密码明文（后端已解密返回）
  mode: 'DERIVED' | 'STORED' // 密码模式：派生 / 存储
  createdAt: string
  updatedAt: string
}

export interface DerivedPasswordResponse {
  entry: PasswordEntry
  password: string
  counter: number
}

export interface VaultStatusResponse {
  initialized: boolean
  authenticated: boolean
}

export interface SetupRequest {
  masterPassword: string
}

export interface LoginRequest {
  masterPassword: string
}

export interface EntryRequest {
  website: string
  username: string
  notes?: string
  counter?: number
  length?: number
  useLowercase?: boolean
  useUppercase?: boolean
  useDigits?: boolean
  useSymbols?: boolean
  password?: string          // 存储模式的密码原文
  mode?: 'DERIVED' | 'STORED' // 密码模式
}

export interface UpdateEntryRequest {
  website?: string
  username?: string
  notes?: string
  length?: number
  useLowercase?: boolean
  useUppercase?: boolean
  useDigits?: boolean
  useSymbols?: boolean
  password?: string          // 存储模式的密码原文（修改时可选）
  mode?: 'DERIVED' | 'STORED' // 密码模式（修改时可选）
}

// ---------- 初始化与认证 ----------

export function vaultSetup(req: SetupRequest): Promise<void> {
  return request<void>('/api/vault/setup', {
    method: 'POST',
    body: JSON.stringify(req),
  })
}

export function vaultLogin(req: LoginRequest): Promise<{ success: boolean }> {
  return request<{ success: boolean }>('/api/vault/login', {
    method: 'POST',
    body: JSON.stringify(req),
  })
}

export function vaultLogout(): Promise<{ success: boolean }> {
  return request<{ success: boolean }>('/api/vault/logout', {
    method: 'POST',
  })
}

export function vaultStatus(): Promise<VaultStatusResponse> {
  return request<VaultStatusResponse>('/api/vault/status')
}

// ---------- 密码条目 CRUD ----------

export function listEntries(): Promise<PasswordEntry[]> {
  return request<PasswordEntry[]>('/api/vault/entries')
}

export function createEntry(req: EntryRequest): Promise<PasswordEntry> {
  return request<PasswordEntry>('/api/vault/entries', {
    method: 'POST',
    body: JSON.stringify(req),
  })
}

export function getEntry(id: number): Promise<PasswordEntry> {
  return request<PasswordEntry>(`/api/vault/entries/${id}`)
}

export function updateEntry(id: number, req: UpdateEntryRequest): Promise<PasswordEntry> {
  return request<PasswordEntry>(`/api/vault/entries/${id}`, {
    method: 'PUT',
    body: JSON.stringify(req),
  })
}

export function deleteEntry(id: number): Promise<void> {
  return request<void>(`/api/vault/entries/${id}`, {
    method: 'DELETE',
  })
}

// ---------- 轮换与派生 ----------

export function rotateEntry(id: number): Promise<PasswordEntry> {
  return request<PasswordEntry>(`/api/vault/entries/${id}/rotate`, {
    method: 'POST',
  })
}

export function derivePassword(id: number, counter?: number): Promise<DerivedPasswordResponse> {
  const query = counter != null ? `?counter=${counter}` : ''
  return request<DerivedPasswordResponse>(`/api/vault/entries/${id}/password${query}`)
}

// ---------- 随机密码生成 ----------

/**
 * 调用后端生成随机密码
 */
export function generatePassword(params: {
  length?: number
  lowercase?: boolean
  uppercase?: boolean
  digits?: boolean
  symbols?: boolean
}): Promise<{ password: string }> {
  const sp = new URLSearchParams()
  if (params.length != null) sp.set('length', String(params.length))
  if (params.lowercase != null) sp.set('lowercase', String(params.lowercase))
  if (params.uppercase != null) sp.set('uppercase', String(params.uppercase))
  if (params.digits != null) sp.set('digits', String(params.digits))
  if (params.symbols != null) sp.set('symbols', String(params.symbols))
  return request<{ password: string }>(`/api/vault/generate-password?${sp.toString()}`)
}

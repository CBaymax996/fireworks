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

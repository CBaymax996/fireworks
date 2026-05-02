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
  return res.json()
}

export interface AuthRequest {
  username: string
  password: string
}

export interface AccountResponse {
  id: number
  username: string
  createdAt: string
}

export interface LoginResponse {
  success: boolean
  account: AccountResponse
}

export interface AuthStatusResponse {
  authenticated: boolean
  accountId: number | null
}

export function register(req: AuthRequest): Promise<AccountResponse> {
  return request<AccountResponse>('/api/auth/register', {
    method: 'POST',
    body: JSON.stringify(req),
  })
}

export function login(req: AuthRequest): Promise<LoginResponse> {
  return request<LoginResponse>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify(req),
  })
}

export function logout(): Promise<{ success: boolean }> {
  return request<{ success: boolean }>('/api/auth/logout', {
    method: 'POST',
  })
}

export function authStatus(): Promise<AuthStatusResponse> {
  return request<AuthStatusResponse>('/api/auth/status')
}

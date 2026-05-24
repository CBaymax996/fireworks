import { useLazyQuery, useMutation } from '@vue/apollo-composable'
import {
  AuthStatus as AUTH_STATUS,
  Register as REGISTER,
  Login as LOGIN,
  Logout as LOGOUT,
} from '@/graphql/auth.gql'

// ── 类型定义 ──

export interface Account {
  id: string
  username: string
  createdAt: string
}

export interface AuthStatusResult {
  authenticated: boolean
  accountId: string | null
}

export interface LoginResult {
  success: boolean
  account: Account
}

// ── Composable ──

export function useAuth() {
  // authStatus 查询（懒加载）
  const { load: loadAuthStatus } = useLazyQuery<{ authStatus: AuthStatusResult }>(AUTH_STATUS, undefined, {
    fetchPolicy: 'network-only',
  })

  // 注册 mutation
  const { mutate: registerMutate } = useMutation<{ register: Account }>(REGISTER)

  // 登录 mutation
  const { mutate: loginMutate } = useMutation<{ login: LoginResult }>(LOGIN)

  // 登出 mutation
  const { mutate: logoutMutate } = useMutation<{ logout: boolean }>(LOGOUT)

  /** 查询当前认证状态 */
  async function authStatus(): Promise<AuthStatusResult> {
    const result = await loadAuthStatus()
    return result!.authStatus
  }

  /** 注册新账户 */
  async function register(username: string, password: string): Promise<Account> {
    const result = await registerMutate({ username, password })
    return result!.data!.register
  }

  /** 登录 */
  async function login(username: string, password: string): Promise<LoginResult> {
    const result = await loginMutate({ username, password })
    return result!.data!.login
  }

  /** 登出 */
  async function logout(): Promise<boolean> {
    const result = await logoutMutate()
    return result!.data!.logout
  }

  return { authStatus, register, login, logout }
}

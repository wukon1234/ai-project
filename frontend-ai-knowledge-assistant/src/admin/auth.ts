import { clearTokens, login, logout as apiLogout, saveTokens } from '../api'
import { USE_ADMIN_MOCK } from './api/config'
import type { AdminRole, AdminUser } from './types'

const AUTHED_KEY = 'zn-admin-authed'
const USER_KEY = 'zn-admin-user'

type MockAccount = {
  email: string
  password: string
  name: string
  role: AdminRole
}

const ACCOUNTS: MockAccount[] = [
  { email: 'admin@zhishiyun.com', password: 'admin123', name: '系统管理员', role: 'SYS_ADMIN' },
  { email: 'kbadmin@zhishiyun.com', password: 'kb123', name: '王婷', role: 'KB_ADMIN' },
  { email: 'zhangming@zhishiyun.com', password: 'any', name: '张明', role: 'EMPLOYEE' },
]

export function readAdminUser(): AdminUser | null {
  if (localStorage.getItem(AUTHED_KEY) !== '1') return null
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as AdminUser
  } catch {
    return null
  }
}

function persistAdminUser(user: AdminUser) {
  localStorage.setItem(AUTHED_KEY, '1')
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}

export function clearAdminSession() {
  localStorage.removeItem(AUTHED_KEY)
  localStorage.removeItem(USER_KEY)
  if (!USE_ADMIN_MOCK) {
    clearTokens()
  }
}

export type AdminLoginResult =
  | { ok: true; user: AdminUser }
  | { ok: false; reason: 'invalid' | 'forbidden' | 'network'; message?: string }

function loginAdminMock(email: string, password: string): AdminLoginResult {
  const normalized = email.trim().toLowerCase()
  const account = ACCOUNTS.find((item) => item.email === normalized)
  if (!account) return { ok: false, reason: 'invalid', message: 'Mock：账号不存在' }

  const passwordOk = account.role === 'EMPLOYEE' ? true : account.password === password
  if (!passwordOk) return { ok: false, reason: 'invalid', message: 'Mock：密码错误' }

  if (account.role === 'EMPLOYEE') {
    return { ok: false, reason: 'forbidden', message: '无管理后台权限' }
  }

  const user: AdminUser = {
    email: account.email,
    name: account.name,
    role: account.role,
  }
  persistAdminUser(user)
  return { ok: true, user }
}

export async function loginAdmin(email: string, password: string): Promise<AdminLoginResult> {
  console.info('[admin-login]', {
    mock: USE_ADMIN_MOCK,
    account: email.trim(),
    apiBase: import.meta.env.VITE_API_BASE_URL ?? '(relative/proxy)',
  })

  if (USE_ADMIN_MOCK) {
    return loginAdminMock(email, password)
  }

  // 先清掉本地旧 token，防止登录请求带过期 Authorization
  clearTokens()
  try {
    const data = await login({
      account: email.trim(),
      password,
      rememberMe: true,
    })
    const role = (data.user?.roleCode || 'EMPLOYEE') as AdminRole
    console.info('[admin-login] ok', { role, userId: data.user?.id })
    if (role !== 'SYS_ADMIN' && role !== 'KB_ADMIN') {
      clearTokens()
      return { ok: false, reason: 'forbidden', message: `当前角色 ${role} 无管理后台权限` }
    }
    saveTokens(data.accessToken, data.refreshToken)
    const user: AdminUser = {
      email: email.trim().toLowerCase(),
      name: data.user.name || email.trim(),
      role,
    }
    persistAdminUser(user)
    return { ok: true, user }
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err)
    console.error('[admin-login] failed', err)
    if (message.includes('权限') || message.includes('禁止')) {
      return { ok: false, reason: 'forbidden', message }
    }
    if (
      message.includes('Failed to fetch') ||
      message.includes('NetworkError') ||
      message.includes('Load failed') ||
      message.includes('网络')
    ) {
      return {
        ok: false,
        reason: 'network',
        message: '无法连接后端（请确认 Vite 代理与 8080 服务）',
      }
    }
    return { ok: false, reason: 'invalid', message: message || '邮箱或密码错误' }
  }
}

export async function logoutAdmin() {
  clearAdminSession()
  if (!USE_ADMIN_MOCK) {
    try {
      await apiLogout()
    } catch {
      /* ignore */
    }
  }
}

export function canAccessAdminView(role: AdminRole, view: string): boolean {
  if (role === 'SYS_ADMIN') return true
  if (role === 'KB_ADMIN') {
    return ['dashboard', 'libraries', 'ingest', 'acl', 'audit'].includes(view)
  }
  return false
}

export const ROLE_LABEL: Record<AdminRole, string> = {
  SYS_ADMIN: '系统管理员',
  KB_ADMIN: '知识管理员',
  EMPLOYEE: '普通员工',
}

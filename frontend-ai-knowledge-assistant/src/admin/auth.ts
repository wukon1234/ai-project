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
  | { ok: false; reason: 'invalid' | 'forbidden' }

function loginAdminMock(email: string, password: string): AdminLoginResult {
  const normalized = email.trim().toLowerCase()
  const account = ACCOUNTS.find((item) => item.email === normalized)
  if (!account) return { ok: false, reason: 'invalid' }

  const passwordOk = account.role === 'EMPLOYEE' ? true : account.password === password
  if (!passwordOk) return { ok: false, reason: 'invalid' }

  if (account.role === 'EMPLOYEE') {
    return { ok: false, reason: 'forbidden' }
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
  if (USE_ADMIN_MOCK) {
    return loginAdminMock(email, password)
  }

  try {
    const data = await login({
      account: email.trim(),
      password,
      rememberMe: true,
    })
    const role = (data.user?.roleCode || 'EMPLOYEE') as AdminRole
    if (role !== 'SYS_ADMIN' && role !== 'KB_ADMIN') {
      clearTokens()
      return { ok: false, reason: 'forbidden' }
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
    const message = err instanceof Error ? err.message : ''
    if (message.includes('权限') || message.includes('禁止')) {
      return { ok: false, reason: 'forbidden' }
    }
    return { ok: false, reason: 'invalid' }
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

export type ApiResult<T> = {
  code: number
  message: string
  data: T
}

export type UserInfo = {
  id: number
  name: string
  deptName: string
  empNo: string
  roleCode: string
  defaultKbScopes: string[]
}

export type AuthResponse = {
  accessToken?: string
  refreshToken?: string
  expiresIn?: number
  user: UserInfo
}

export type LoginPayload = {
  account: string
  password: string
  rememberMe: boolean
}

export type RegisterPayload = {
  name: string
  email: string
  password: string
}

// 开发环境走 Vite 代理（见 vite.config.ts）；生产可设 VITE_API_BASE_URL
const API_BASE = import.meta.env.VITE_API_BASE_URL ?? ''
const ACCESS_TOKEN_KEY = 'zn-access-token'
const REFRESH_TOKEN_KEY = 'zn-refresh-token'

function getAccessToken() {
  return localStorage.getItem(ACCESS_TOKEN_KEY)
}

export function saveTokens(accessToken?: string, refreshToken?: string) {
  if (accessToken) localStorage.setItem(ACCESS_TOKEN_KEY, accessToken)
  if (refreshToken) localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken)
}

export function clearTokens() {
  localStorage.removeItem(ACCESS_TOKEN_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)
}

function getRefreshToken() {
  return localStorage.getItem(REFRESH_TOKEN_KEY)
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(init?.headers as Record<string, string> | undefined),
  }
  const token = getAccessToken()
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }
  const resp = await fetch(`${API_BASE}${path}`, { ...init, headers })
  const json = (await resp.json()) as ApiResult<T>
  if (!resp.ok || json.code !== 0) {
    throw new Error(json.message || '请求失败')
  }
  return json.data
}

export function login(payload: LoginPayload) {
  return request<AuthResponse>('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function register(payload: RegisterPayload) {
  return request<AuthResponse>('/api/v1/auth/register', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function me() {
  return request<AuthResponse>('/api/v1/auth/me')
}

export function logout() {
  return request<null>('/api/v1/auth/logout', {
    method: 'POST',
    body: JSON.stringify({ refreshToken: getRefreshToken() }),
  })
}

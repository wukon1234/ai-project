import type { ApiResult } from '../../api'

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? ''
const ACCESS_TOKEN_KEY = 'zn-access-token'

export class AdminApiError extends Error {
  code: number
  httpStatus: number

  constructor(message: string, code: number, httpStatus: number) {
    super(message)
    this.name = 'AdminApiError'
    this.code = code
    this.httpStatus = httpStatus
  }
}

function getAccessToken() {
  return localStorage.getItem(ACCESS_TOKEN_KEY)
}

function authHeaders(json = true): Record<string, string> {
  const headers: Record<string, string> = {}
  if (json) headers['Content-Type'] = 'application/json'
  const token = getAccessToken()
  if (token) headers.Authorization = `Bearer ${token}`
  return headers
}

function handleUnauthorized() {
  localStorage.removeItem('zn-admin-authed')
  localStorage.removeItem('zn-admin-user')
  const url = new URL(window.location.href)
  url.searchParams.set('app', 'admin')
  url.searchParams.delete('adminView')
  window.history.replaceState({}, '', url.pathname + url.search + url.hash)
  window.dispatchEvent(new CustomEvent('zn-admin-unauthorized'))
}

export async function adminRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const isForm = typeof FormData !== 'undefined' && init?.body instanceof FormData
  const headers: Record<string, string> = {
    ...authHeaders(!isForm),
    ...(init?.headers as Record<string, string> | undefined),
  }
  if (isForm) delete headers['Content-Type']

  const resp = await fetch(`${API_BASE}${path}`, { ...init, headers })
  const json = (await resp.json()) as ApiResult<T>

  if (resp.status === 401 || json.code === 40101) {
    handleUnauthorized()
    throw new AdminApiError(json.message || '未登录', json.code || 40101, 401)
  }
  if (!resp.ok || json.code !== 0) {
    throw new AdminApiError(json.message || '请求失败', json.code ?? resp.status, resp.status)
  }
  return json.data
}

export async function adminRequestBlob(path: string, init?: RequestInit): Promise<Blob> {
  const headers: Record<string, string> = {
    ...authHeaders(false),
    ...(init?.headers as Record<string, string> | undefined),
  }
  const resp = await fetch(`${API_BASE}${path}`, { ...init, headers })
  if (resp.status === 401) {
    handleUnauthorized()
    throw new AdminApiError('未登录', 40101, 401)
  }
  if (!resp.ok) {
    let message = '导出失败'
    try {
      const json = (await resp.json()) as ApiResult<unknown>
      message = json.message || message
    } catch {
      /* ignore */
    }
    throw new AdminApiError(message, resp.status, resp.status)
  }
  return resp.blob()
}

export function qs(params: Record<string, string | number | undefined | null>): string {
  const sp = new URLSearchParams()
  Object.entries(params).forEach(([k, v]) => {
    if (v === undefined || v === null || v === '') return
    sp.set(k, String(v))
  })
  const s = sp.toString()
  return s ? `?${s}` : ''
}

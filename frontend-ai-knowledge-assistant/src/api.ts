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

export type ChatSession = {
  id: number
  userId?: number
  title: string
  scope: string
  shareToken?: string
  lastQuestion?: string
  rating?: number
  messageCount?: number
  updatedAt?: string
  createdAt?: string
}

export type ChatMessage = {
  id: number
  sessionId: number
  role: 'user' | 'assistant' | string
  content?: string
  answerStatus?: string
  elapsedMs?: number
  createdAt?: string
}

export type ChatCitation = {
  id?: number
  messageId?: number
  citeIndex?: number
  docId: number | string
  title: string
  pageNo: number
  libraryName?: string
  libraryCode?: string
  excerpt: string
}

export type SessionDetail = {
  session: ChatSession
  messages: ChatMessage[]
  citations: ChatCitation[]
}

export type StreamCitation = {
  index: number
  docId: string
  title: string
  page: number
  knowledgeBase: string
  knowledgeBaseId: string
  excerpt: string
}

export type StreamDone = {
  elapsedMs: number
  status: 'OK' | 'NO_ANSWER' | string
  disclaimer?: string
  suggestions?: string[]
  contact?: {
    name: string
    title: string
    wecom?: string
    extNo?: string
  }
}

export type DocumentMeta = {
  id: number
  title: string
  knowledgeBase: string
  knowledgeBaseId: string
  fileType: string
  pages: number
  summary?: string
  updatedAt?: string
  views?: number
  favorited?: boolean
  category?: string
}

export type StreamHandlers = {
  onMeta?: (data: { messageId?: string; status?: string; traceId?: string }) => void
  onCitation?: (data: StreamCitation) => void
  onDelta?: (data: { content: string }) => void
  onDone?: (data: StreamDone) => void
  onError?: (data: { code?: number; message?: string }) => void
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

function authHeaders(json = true): Record<string, string> {
  const headers: Record<string, string> = {}
  if (json) headers['Content-Type'] = 'application/json'
  const token = getAccessToken()
  if (token) headers.Authorization = `Bearer ${token}`
  return headers
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const headers: Record<string, string> = {
    ...authHeaders(true),
    ...(init?.headers as Record<string, string> | undefined),
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

export function createSession(scope?: string) {
  return request<ChatSession>('/api/v1/chat/sessions', {
    method: 'POST',
    body: JSON.stringify(scope ? { scope } : {}),
  })
}

export function listSessions(keyword?: string) {
  const q = keyword?.trim() ? `?keyword=${encodeURIComponent(keyword.trim())}` : ''
  return request<ChatSession[]>(`/api/v1/chat/sessions${q}`)
}

export function getSession(id: number) {
  return request<SessionDetail>(`/api/v1/chat/sessions/${id}`)
}

export function deleteSession(id: number) {
  return request<null>(`/api/v1/chat/sessions/${id}`, { method: 'DELETE' })
}

export function batchDeleteSessions(ids: number[]) {
  return request<null>('/api/v1/chat/sessions/batch-delete', {
    method: 'POST',
    body: JSON.stringify({ ids }),
  })
}

export function patchSessionScope(id: number, scope: string) {
  return request<ChatSession>(`/api/v1/chat/sessions/${id}/scope`, {
    method: 'PATCH',
    body: JSON.stringify({ scope }),
  })
}

export function clearSession(id: number) {
  return request<null>(`/api/v1/chat/sessions/${id}/clear`, { method: 'POST' })
}

export function shareSession(id: number) {
  return request<{ shareToken?: string; shareUrl?: string } | string>(`/api/v1/chat/sessions/${id}/share`, {
    method: 'POST',
  })
}

export function getDocumentMeta(id: number | string) {
  return request<DocumentMeta>(`/api/v1/documents/${id}`)
}

export function viewDocument(
  id: number | string,
  opts?: { pageNo?: number; eventType?: string; readMinutes?: number },
) {
  const params = new URLSearchParams()
  if (opts?.pageNo != null) params.set('pageNo', String(opts.pageNo))
  if (opts?.eventType) params.set('eventType', opts.eventType)
  if (opts?.readMinutes != null) params.set('readMinutes', String(opts.readMinutes))
  const q = params.toString()
  return request<{ views: number }>(`/api/v1/documents/${id}/view${q ? `?${q}` : ''}`, {
    method: 'POST',
  })
}

export function shareDocument(id: number | string) {
  return request<{ shareToken: string; shareUrl: string; expireHours: number }>(
    `/api/v1/documents/${id}/share`,
    { method: 'POST' },
  )
}

export function documentFileUrl(id: number | string, download = false) {
  const token = getAccessToken()
  const q = new URLSearchParams()
  if (download) q.set('download', 'true')
  // 供 <a>/<iframe>：带 token 不便，下载用 fetch blob；预览 URL 仅路径
  return `${API_BASE}/api/v1/documents/${id}/file${q.toString() ? `?${q}` : ''}${token ? '' : ''}`
}

export async function downloadDocument(id: number | string, filename?: string) {
  const resp = await fetch(`${API_BASE}/api/v1/documents/${id}/file?download=true`, {
    headers: authHeaders(false),
  })
  if (!resp.ok) throw new Error('下载失败')
  const blob = await resp.blob()
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename || `document-${id}.pdf`
  a.click()
  URL.revokeObjectURL(url)
}

export async function fetchDocumentBlobUrl(id: number | string) {
  const resp = await fetch(`${API_BASE}/api/v1/documents/${id}/file`, {
    headers: authHeaders(false),
  })
  if (!resp.ok) throw new Error('加载原文失败')
  const blob = await resp.blob()
  return URL.createObjectURL(blob)
}

async function consumeSse(path: string, body: unknown | undefined, handlers: StreamHandlers) {
  const resp = await fetch(`${API_BASE}${path}`, {
    method: 'POST',
    headers: authHeaders(true),
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (!resp.ok || !resp.body) {
    let message = '流式请求失败'
    try {
      const json = (await resp.json()) as ApiResult<unknown>
      message = json.message || message
    } catch {
      // ignore
    }
    handlers.onError?.({ code: resp.status, message })
    return
  }

  const reader = resp.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let eventName = 'message'
  let dataLines: string[] = []

  const flush = () => {
    if (!dataLines.length) return
    const raw = dataLines.join('\n')
    dataLines = []
    const name = eventName
    eventName = 'message'
    let payload: Record<string, unknown> = {}
    try {
      payload = JSON.parse(raw) as Record<string, unknown>
    } catch {
      payload = { content: raw }
    }
    if (name === 'meta') handlers.onMeta?.(payload as never)
    else if (name === 'citation') handlers.onCitation?.(payload as never)
    else if (name === 'delta') handlers.onDelta?.(payload as never)
    else if (name === 'done') handlers.onDone?.(payload as never)
    else if (name === 'error') handlers.onError?.(payload as never)
  }

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const parts = buffer.split(/\r?\n/)
    buffer = parts.pop() ?? ''
    for (const line of parts) {
      if (line.startsWith('event:')) {
        eventName = line.slice(6).trim()
      } else if (line.startsWith('data:')) {
        dataLines.push(line.slice(5).trim())
      } else if (line === '') {
        flush()
      }
    }
  }
  flush()
}

export function askStream(sessionId: number, question: string, handlers: StreamHandlers) {
  return consumeSse(`/api/v1/chat/sessions/${sessionId}/messages:stream`, { question }, handlers)
}

export function regenerateStream(messageId: number, handlers: StreamHandlers) {
  return consumeSse(`/api/v1/chat/messages/${messageId}/regenerate:stream`, undefined, handlers)
}

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
  messageId?: string | number
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

export type FeedbackIssueType =
  | 'INACCURATE'
  | 'WRONG_DOC'
  | 'MISSING_KNOWLEDGE'
  | 'INCOMPLETE'
  | 'OTHER'

export type SearchResultItem = {
  id: string
  title: string
  fileType: 'pdf' | 'word' | 'excel' | 'ppt' | 'image' | string
  category: string
  knowledgeBase: string
  pages: number
  updatedAt: string
  views: number
  excerptBefore: string
  highlights: string[]
  excerptAfter: string
  page: number
}

export type SearchPage = {
  page: number
  size: number
  total: number
  list: SearchResultItem[]
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

export type StreamMeta = {
  messageId?: string
  status?: string
  phase?: 'search' | 'recognize' | 'think' | 'answer' | string
  message?: string
  hitCount?: number
  traceId?: string
  docId?: string
}

export type StreamHandlers = {
  onMeta?: (data: StreamMeta) => void
  onCitation?: (data: StreamCitation) => void
  onThinking?: (data: { content: string }) => void
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

async function requestResult<T>(path: string, init?: RequestInit): Promise<ApiResult<T>> {
  const headers: Record<string, string> = {
    ...authHeaders(true),
    ...(init?.headers as Record<string, string> | undefined),
  }
  const resp = await fetch(`${API_BASE}${path}`, { ...init, headers })
  const json = (await resp.json()) as ApiResult<T>
  if (!resp.ok || json.code !== 0) {
    throw new Error(json.message || '请求失败')
  }
  return json
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const json = await requestResult<T>(path, init)
  return json.data
}

/** 登录/注册不携带旧 Authorization，避免过期 token 被网关/过滤器直接 401。 */
async function requestAnonymous<T>(path: string, init?: RequestInit): Promise<T> {
  const url = `${API_BASE}${path}`
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(init?.headers as Record<string, string> | undefined),
  }
  delete headers.Authorization
  console.info('[api]', init?.method || 'GET', url)
  let resp: Response
  try {
    resp = await fetch(url, { ...init, headers })
  } catch (err) {
    console.error('[api] network error', url, err)
    throw new Error('网络请求失败，无法连接后端')
  }
  const text = await resp.text()
  let json: ApiResult<T>
  try {
    json = JSON.parse(text) as ApiResult<T>
  } catch {
    console.error('[api] non-json response', resp.status, text.slice(0, 200))
    throw new Error(`后端响应异常 HTTP ${resp.status}`)
  }
  if (!resp.ok || json.code !== 0) {
    console.warn('[api] business error', { status: resp.status, code: json.code, message: json.message })
    throw new Error(json.message || `请求失败(${json.code || resp.status})`)
  }
  return json.data
}

export function login(payload: LoginPayload) {
  return requestAnonymous<AuthResponse>('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function register(payload: RegisterPayload) {
  return requestAnonymous<AuthResponse>('/api/v1/auth/register', {
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
  if (!resp.ok) {
    let message = '暂无原文文件'
    try {
      const json = (await resp.json()) as ApiResult<unknown>
      if (json.message && json.message !== '系统错误') message = json.message
    } catch {
      // ignore
    }
    throw new Error(message)
  }
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
  if (!resp.ok) {
    let message = '暂无原文文件'
    try {
      const json = (await resp.json()) as ApiResult<unknown>
      if (json.message && json.message !== '系统错误') message = json.message
    } catch {
      // ignore
    }
    throw new Error(message)
  }
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
    else if (name === 'thinking') handlers.onThinking?.(payload as never)
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

export async function feedbackHelpful(messageId: number) {
  const res = await requestResult<null>('/api/v1/feedback/helpful', {
    method: 'POST',
    body: JSON.stringify({ messageId }),
  })
  return res.message || '感谢反馈，我们会尽快优化'
}

export async function feedbackUnhelpful(payload: {
  messageId: number
  issueType: FeedbackIssueType
  comment?: string
  knowCorrect: boolean
  correctAnswer?: string
}) {
  const res = await requestResult<null>('/api/v1/feedback/unhelpful', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
  return res.message || '感谢反馈，我们会尽快优化'
}

export async function feedbackRating(messageId: number, score: number) {
  const res = await requestResult<null>('/api/v1/feedback/rating', {
    method: 'POST',
    body: JSON.stringify({ messageId, score }),
  })
  return res.message || '感谢反馈，我们会尽快优化'
}

export function searchKnowledge(params: {
  q?: string
  category?: string
  sort?: 'relevance' | 'updated' | 'views' | string
  page?: number
  size?: number
}) {
  const qs = new URLSearchParams()
  if (params.q != null) qs.set('q', params.q)
  if (params.category) qs.set('category', params.category)
  if (params.sort) qs.set('sort', params.sort)
  if (params.page != null) qs.set('page', String(params.page))
  if (params.size != null) qs.set('size', String(params.size))
  const q = qs.toString()
  return request<SearchPage>(`/api/v1/search/knowledge${q ? `?${q}` : ''}`)
}

export function searchHot() {
  return request<string[]>('/api/v1/search/hot')
}

export type LibraryItem = {
  code: string
  name: string
  description?: string
  docCount?: number
  tags?: string[] | string
  updatedAt?: string
}

export type LibraryDocItem = {
  id: string | number
  libraryId: string
  title: string
  category: string
  pages: number
  updatedAt?: string
  views?: number
  page?: number
  summary?: string
}

export type HistoryItemApi = {
  id: number
  title: string
  lastQuestion?: string
  scope?: string
  updatedAt?: string
  group?: string
  rating?: number
}

export type FavDocApi = {
  id: number
  docId: number
  page?: number
  title?: string
  category?: string
  knowledgeBase?: string
  savedAt?: string
}

export type FavAnswerApi = {
  id: number
  messageId?: number
  summary?: string
  topic?: string
  source?: string
  savedAt?: string
  context?: string[]
}

export type UserPreferences = {
  notifyKbUpdate: number
  notifyMention: number
  themeMode: string
  defaultKbScopes: string[]
}

export type ProfileInfo = {
  id: number
  name: string
  deptName?: string
  empNo?: string
  roleCode?: string
  preferences?: UserPreferences
}

export function listLibraries() {
  return request<LibraryItem[]>('/api/v1/libraries')
}

export function listLibraryDocuments(
  code: string,
  params?: { category?: string; q?: string; page?: number; size?: number },
) {
  const qs = new URLSearchParams()
  if (params?.category) qs.set('category', params.category)
  if (params?.q != null) qs.set('q', params.q)
  if (params?.page != null) qs.set('page', String(params.page))
  if (params?.size != null) qs.set('size', String(params.size))
  const q = qs.toString()
  return request<{ page: number; size: number; total: number; list: LibraryDocItem[] }>(
    `/api/v1/libraries/${encodeURIComponent(code)}/documents${q ? `?${q}` : ''}`,
  )
}

export function listHistory(keyword?: string) {
  const q = keyword?.trim() ? `?keyword=${encodeURIComponent(keyword.trim())}` : ''
  return request<HistoryItemApi[]>(`/api/v1/history${q}`)
}

export function listFavoriteDocuments() {
  return request<FavDocApi[]>('/api/v1/favorites/documents')
}

export function saveFavoriteDocument(docId: number, page?: number) {
  return request<null>('/api/v1/favorites/documents', {
    method: 'POST',
    body: JSON.stringify({ docId, page }),
  })
}

export function deleteFavoriteDocument(docId: number) {
  return request<null>(`/api/v1/favorites/documents/${docId}`, { method: 'DELETE' })
}

export function listFavoriteAnswers() {
  return request<FavAnswerApi[]>('/api/v1/favorites/answers')
}

export function saveFavoriteAnswer(payload: { messageId: number; summary?: string; topic?: string }) {
  return request<null>('/api/v1/favorites/answers', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function deleteFavoriteAnswer(id: number) {
  return request<null>(`/api/v1/favorites/answers/${id}`, { method: 'DELETE' })
}

export function getProfile() {
  return request<ProfileInfo>('/api/v1/profile')
}

export function getPreferences() {
  return request<UserPreferences>('/api/v1/profile/preferences')
}

export function updatePreferences(prefs: UserPreferences) {
  return request<UserPreferences>('/api/v1/profile/preferences', {
    method: 'PUT',
    body: JSON.stringify(prefs),
  })
}

export type PageSummary = {
  pageNo: number
  knowledgeBase?: string
  summary: string
  cached?: boolean
}

export type RelatedChunk = {
  page: number
  title?: string
  summary?: string
  excerpt?: string
  chunkId?: number
}

export function getPageSummary(docId: number | string, pageNo: number) {
  return request<PageSummary>(`/api/v1/documents/${docId}/pages/${pageNo}/summary`)
}

export function getRelatedChunks(docId: number | string, pageNo: number, limit = 5) {
  return request<RelatedChunk[]>(
    `/api/v1/documents/${docId}/related-chunks?pageNo=${pageNo}&limit=${limit}`,
  )
}

export function documentAskStream(docId: number | string, question: string, handlers: StreamHandlers) {
  return consumeSse(`/api/v1/documents/${docId}/ask:stream`, { question }, handlers)
}

export type StatsOverview = {
  range?: string
  from?: string
  to?: string
  updatedAt?: string
  kpi?: {
    askCount?: number
    askMomPercent?: number
    savedHours?: number
    avgRating?: number
    ratingCount?: number
    favoriteCount?: number
    favoriteMonthNew?: number
  }
  askTrend?: Array<{ date: string; count: number }>
  libraryDistribution?: Array<{ libraryCode: string; count: number; percent: number }>
  topQuestions?: Array<{ question: string; count: number }>
  feedbackOverview?: {
    helpful?: number
    unhelpful?: number
    helpfulPercent?: number
    optimizedHint?: string
  }
  achievements?: Array<{
    name: string
    description?: string
    current?: number
    target?: number
    completed?: boolean
    progress?: number
  }>
  activeHeatmap?: Array<{ weekday: number; hour: number; count: number }>
  sourceHabit?: {
    openSourceCount?: number
    readCompleteCount?: number
    avgReadMinutes?: number | null
  }
}

export function getStatsOverview(params: {
  range?: string
  from?: string
  to?: string
}) {
  const qs = new URLSearchParams()
  if (params.range) qs.set('range', params.range)
  if (params.from) qs.set('from', params.from)
  if (params.to) qs.set('to', params.to)
  const q = qs.toString()
  return request<StatsOverview>(`/api/v1/stats/overview${q ? `?${q}` : ''}`)
}

export async function exportStats(params: { range?: string; from?: string; to?: string }) {
  const qs = new URLSearchParams()
  if (params.range) qs.set('range', params.range)
  if (params.from) qs.set('from', params.from)
  if (params.to) qs.set('to', params.to)
  const q = qs.toString()
  const resp = await fetch(`${API_BASE}/api/v1/stats/export${q ? `?${q}` : ''}`, {
    headers: authHeaders(false),
  })
  if (!resp.ok) throw new Error('导出失败')
  const blob = await resp.blob()
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `zhishiyun-stats-${params.range || 'report'}.csv`
  a.click()
  URL.revokeObjectURL(url)
}

export type HelpFaq = {
  id?: number
  question: string
  answer: string
  locale?: string
  sortNo?: number
}

export function listFaqs(locale = 'zh-CN') {
  return request<HelpFaq[]>(`/api/v1/help/faqs?locale=${encodeURIComponent(locale)}`)
}

export function forgotPassword(email: string) {
  return request<{ accepted?: boolean; devResetToken?: string }>('/api/v1/auth/password/forgot', {
    method: 'POST',
    body: JSON.stringify({ email }),
  })
}

export function resetPassword(token: string, newPassword: string) {
  return request<null>('/api/v1/auth/password/reset', {
    method: 'POST',
    body: JSON.stringify({ token, newPassword }),
  })
}

export function ssoAuthorizeUrl() {
  return `${API_BASE}/api/v1/auth/sso/authorize`
}

export function getSharedSession(token: string) {
  return request<{
    title?: string
    scope?: string
    messages?: Array<{ role?: string; content?: string; answerStatus?: string }>
    requireLogin?: boolean
  }>(`/api/v1/share/sessions/${encodeURIComponent(token)}`)
}

export function getSharedDocument(token: string) {
  return request<{
    id?: number
    title?: string
    libraryCode?: string
    pages?: number
    summary?: string
    fileType?: string
    requireLogin?: boolean
  }>(`/api/v1/share/documents/${encodeURIComponent(token)}`)
}
